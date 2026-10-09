#!/usr/bin/env python3
"""Render /etc/flexisip/{flexisip.conf,providers.json,users.auth} from /config and environment variables."""
import json
import os
import re
import sys
from string import Template

CONFIG_DIR = os.environ.get("CONFIG_DIR", "/config")
OUT_DIR = os.environ.get("OUT_DIR", "/etc/flexisip")
# Characters accepted in a password by the Flexisip auth file grammar (share/authdb-file-grammar.txt).
TOKEN = re.compile(r"^[A-Za-z0-9\-.!%*_+`'~:]+$")


def fail(msg):
    sys.exit(f"render-config: {msg}")


def env(name, default=None):
    value = os.environ.get(name, default)
    if not value:
        fail(f"environment variable {name} is not set (see .env)")
    return value


def read_accounts(path):
    accounts = []
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            fields = line.split()
            if len(fields) not in (3, 4):
                fail(f"{path}:{lineno}: expected 'extension s50_password app_password [s50_auth_name]'")
            ext, s50_password, app_password = fields[:3]
            auth_name = fields[3] if len(fields) == 4 else ""
            if not TOKEN.match(ext):
                fail(f"{path}:{lineno}: invalid extension '{ext}'")
            if not TOKEN.match(app_password):
                fail(f"{path}:{lineno}: app password may only contain letters, digits and - . ! % * _ + ` ' ~ :")
            accounts.append((ext, s50_password, app_password, auth_name))
    if not accounts:
        fail(f"{path} contains no account")
    return accounts


def main():
    values = {
        "FLEXISIP_IP": env("FLEXISIP_IP"),
        "S50_HOST": env("S50_HOST"),
        "S50_PORT": env("S50_PORT", "5060"),
        "FIREBASE_PROJECT_NUMBER": env("FIREBASE_PROJECT_NUMBER"),
        "AUDIO_CODEC": env("AUDIO_CODEC", "PCMU/8000"),
        "LOG_LEVEL": env("LOG_LEVEL", "message"),
    }
    accounts = read_accounts(os.path.join(CONFIG_DIR, "accounts.conf"))

    with open(os.path.join(CONFIG_DIR, "flexisip.conf.template"), encoding="utf-8") as f:
        conf = Template(f.read()).substitute(values)

    s50 = f"{values['S50_HOST']}:{values['S50_PORT']}"
    flexisip = values["FLEXISIP_IP"]
    loader = []
    for ext, s50_password, _, auth_name in accounts:
        account = {
            "uri": f"sip:{ext}@{s50}",
            "alias": f"sip:{ext}@{flexisip}",
            "secretType": "clrtxt",
            "secret": s50_password,
        }
        if auth_name:
            account["userid"] = auth_name
        loader.append(account)

    providers = {
        "schemaVersion": 2,
        "providers": [
            {
                # App user calls out: use the S50 extension mapped to the caller.
                "name": "App -> S50 (outbound)",
                "triggerCondition": {"strategy": "Always"},
                "accountToUse": {"strategy": "FindInPool", "source": "{from}", "by": "alias"},
                "onAccountNotFound": "nextProvider",
                "outgoingInvite": {
                    "to": "sip:{incoming.to.user}@{account.uri.hostport}{incoming.to.uriParameters}",
                    "from": "{incoming.from.displayName} {account.uri}",
                },
                "accountPool": "S50",
            },
            {
                # S50 rings an extension held by the bridge: forward to the app through the proxy (push if asleep).
                "name": "S50 -> App (inbound)",
                "triggerCondition": {"strategy": "Always"},
                "accountToUse": {"strategy": "FindInPool", "source": "{to.user}", "by": "{uri.user}"},
                "onAccountNotFound": "decline",
                "outgoingInvite": {
                    "to": "{account.alias}",
                    "from": "{incoming.from.displayName} sip:{incoming.from.user}@{account.alias.hostport}",
                    "outboundProxy": f"<sip:{flexisip}:5060;transport=udp>",
                },
                "accountPool": "S50",
            },
        ],
        "accountPools": {
            "S50": {
                "outboundProxy": f"<sip:{s50};transport=udp>",
                "registrationRequired": True,
                "maxCallsPerLine": 4,
                "loader": loader,
            }
        },
    }

    users = ["version:1"]
    users += [f"{ext}@{flexisip} clrtxt:{app_password} ;" for ext, _, app_password, _ in accounts]

    os.makedirs(OUT_DIR, exist_ok=True)
    for name, content, mode in (
        ("flexisip.conf", conf, 0o644),
        ("providers.json", json.dumps(providers, indent=2, ensure_ascii=False) + "\n", 0o600),
        ("users.auth", "\n".join(users) + "\n", 0o600),
    ):
        path = os.path.join(OUT_DIR, name)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        os.chmod(path, mode)
    print(f"render-config: {len(accounts)} account(s) written to {OUT_DIR}")


if __name__ == "__main__":
    main()
