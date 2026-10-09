# Flexisip 推送网关（Yeastar S50 + Linphone）

让 Linphone 在 App 被杀掉后，来电时通过谷歌 FCM 推送唤醒。S50 的配置几乎不用动。

```
                以分机身份注册（一直在线）
  S50  ◀──────────────────────────────▶  b2bua（sip-bridge）
                                              │
                                              ▼
                                         proxy ── 手机不在线 → FCM 推送 → 手机醒来并注册 → 接通
                                              ▲
  Linphone 账号 A ───── 注册到 proxy ─────────┘
```

| 容器 | 作用 |
|---|---|
| `redis` | 保存手机的注册信息和推送令牌，重启后不丢失 |
| `proxy` | 手机注册到这里；来电时如果 App 已睡眠，就发 FCM 推送，等手机注册上来再转接 |
| `b2bua` | 用分机账号注册到 S50；把 S50 的来电转给 proxy，把手机打出的电话转给 S50 |

所有电话（包括分机互打、外线）都经过 S50，录音、话单、外线路由照常由 S50 负责。

## 账号说明

**不占用额外的 S50 分机。** 每个手机用户对应 S50 上的一个分机，比如 801：

| 账号 | 在哪里 | 用户名 | 密码 |
|---|---|---|---|
| S50 分机 801 | S50 | 801 | S50 上的分机密码，填在 `accounts.conf` 里，只有 b2bua 使用 |
| Linphone 账号 A | Flexisip | 801 | 自己定的 App 密码，填在 `accounts.conf` 里 |
| Linphone 账号 B（备用） | 直连 S50 | 801 | S50 分机密码，平时关闭 |

账号 A 和账号 B 用的是**同一个分机**。账号 B 只在 Flexisip 停用时手动打开，**两个不要同时启用**，否则 S50 上 801 会有两处注册。

## 部署步骤

### 1. S50 设置

- 分机 801：编解码器启用 `u-law`（PCMU）。如果你用 `a-law`，把 `.env` 里的 `AUDIO_CODEC` 改成 `PCMA/8000`。
- 分机 801：振铃超时设 40 秒以上，给推送和唤醒留出时间。
- 安全设置：把 Flexisip 主机的 IP 加入白名单，避免被自动防御功能封锁。

### 2. Firebase

1. 在 Firebase 控制台建一个项目，添加 Android App，包名就是你自己编译的 Linphone 的包名。
2. 进入"项目设置 > 常规"，记下 **项目编号**（Project number），填到 `.env` 的 `FIREBASE_PROJECT_NUMBER`。
3. 进入"项目设置 > 服务账号"，点"生成新的私钥"，把下载的 JSON 保存为 `secrets/firebase-service-account.json`。
   这个文件就是私钥，**不要提交到 Git**（`.gitignore` 已排除）。

> 注意：从应用商店下载的官方 Linphone 用的是 Belledonne 自己的 Firebase 项目，收不到你的服务器发的推送。
> 必须用接了**同一个 Firebase 项目**的自编译 Linphone，推送才会生效。
> 在这之前，App 在前台或后台运行时可以正常打电话，只是 App 被杀后不会被唤醒。

### 3. 配置

```bash
cd flexisip
cp .env.example .env                              # 填写 IP、项目编号
cp config/accounts.conf.example config/accounts.conf   # 每个分机一行
```

### 4. 构建和启动

```bash
docker compose build        # 从源码编译 Flexisip，第一次需要 30–60 分钟，建议 8 GB 内存
docker compose up -d
docker compose logs -f proxy b2bua
```

构建时需要能访问 `gitlab.linphone.org`。

启动后可以在 S50 网页后台确认分机 801 显示"已注册"，注册来源 IP 是 Flexisip 主机。

### 5. Linphone 账号 A

| 项目 | 填写 |
|---|---|
| 用户名 | `801` |
| 密码 | `accounts.conf` 里的 App 密码 |
| 域名 | `FLEXISIP_IP`，例如 `192.168.1.20`（**不要带端口**） |
| 传输协议 | UDP |
| 推送通知 | 开启 |

## 修改账号

修改 `config/accounts.conf` 后运行：

```bash
docker compose restart proxy b2bua
```

## 试点测试清单

- [ ] App 在前台时来电和去电
- [ ] App 被划掉后，分机互打能唤醒
- [ ] App 被划掉后，外线来电能唤醒
- [ ] 锁屏放一晚后来电
- [ ] 来电显示正确（主叫号码和名称）
- [ ] 转接和保持
- [ ] `docker compose restart` 后不用打开 App 也能接到来电
- [ ] S50 重启后，b2bua 能自动重新注册

## 常见问题

| 现象 | 排查 |
|---|---|
| S50 上分机未注册 | 看 `docker compose logs b2bua`：S50 密码、`s50_auth_name`、S50 白名单 |
| App 注册失败（401/403） | App 密码、域名是否与 `FLEXISIP_IP` 完全一致 |
| 来电时 App 没被唤醒 | `logs proxy` 里有没有 push 相关错误；Firebase 项目编号；是否用自编译的 App；手机电池优化 |
| 接通后没有声音 | S50 分机和 `AUDIO_CODEC` 的编解码器是否一致 |

需要更详细的日志时，把 `.env` 里的 `LOG_LEVEL` 改成 `debug`，再重启容器。

## 文件说明

| 文件 | 说明 |
|---|---|
| `Dockerfile` | 从源码编译 Flexisip 2.6.2 |
| `docker-compose.yml` | redis、proxy、b2bua 三个容器，使用 host 网络 |
| `config/flexisip.conf.template` | Flexisip 配置模板 |
| `config/accounts.conf` | 分机和密码（不提交 Git） |
| `render-config.py` | 容器启动时生成 `flexisip.conf`、`providers.json`、`users.auth` |
| `secrets/firebase-service-account.json` | Firebase 私钥（不提交 Git） |
