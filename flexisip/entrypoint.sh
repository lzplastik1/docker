#!/bin/sh
# Usage: entrypoint.sh proxy|b2bua
set -e
/usr/local/bin/render-config.py
# The FCM access-token script (Python requests) reads the proxy from the environment.
if [ -n "$PUSH_PROXY" ]; then
  export https_proxy="$PUSH_PROXY" HTTPS_PROXY="$PUSH_PROXY"
fi
exec flexisip --server "${1:-proxy}" -c /etc/flexisip/flexisip.conf
