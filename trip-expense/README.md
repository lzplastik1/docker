# 出差钱袋

出差公共钱袋记账 APP（单机版，数据只存手机本地）。设计方案见 [方案.md](方案.md)。

## 装到手机

1. 用手机浏览器打开 APP 网址（见下）。
2. iPhone：Safari → 分享 → 「添加到主屏幕」。安卓：Chrome → 菜单 → 「安装应用 / 添加到主屏幕」。
3. 之后从桌面图标打开，断网也能用。

## 网址

- GitHub Pages（合并到 master 并开启 Pages 后）：`https://lzplastik1.github.io/docker/`
- 开启 Pages：仓库 Settings → Pages → Source 选 **GitHub Actions**。

## 文件

| 文件 | 作用 |
|---|---|
| `index.html` | 整个 APP |
| `manifest.webmanifest`、`icon-*.png` | 添加到主屏幕用的名称和图标 |
| `sw.js` | 离线缓存 |

数据保存在浏览器本地，换手机或清理浏览器前请在「设置 → 备份 / 恢复」导出备份。
