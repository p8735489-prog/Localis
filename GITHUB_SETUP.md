# GitHub 上传与固定签名配置

本项目不会把 `localis-release.jks`、密码或 Base64 私钥提交到仓库。

## GitHub Secrets

在仓库 `Settings → Secrets and variables → Actions → New repository secret` 添加：

- `SIGNING_KEYSTORE_B64`：`localis-release.jks` 的 Base64 内容
- `SIGNING_STORE_PASSWORD`：签名库密码
- `SIGNING_KEY_ALIAS`：`localis`
- `SIGNING_KEY_PASSWORD`：签名密钥密码

## 构建规则

- push 到 `main` / `master`：构建 Debug APK，不需要签名 Secrets
- Pull Request：构建 Debug APK
- 推送 `v*` 标签：使用固定签名构建 Release APK，并创建 GitHub Release

请勿把 `.jks`、`.keystore`、密码或 `keystore_base64.txt` 提交到公开仓库。
