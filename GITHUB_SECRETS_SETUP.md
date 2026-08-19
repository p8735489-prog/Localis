# Localis GitHub Actions 签名配置指南

## 问题诊断

构建日志显示以下错误：

```
Build file 'app/build.gradle.kts' line: 60
Release signing credentials are missing.
Configure SIGNING_KEYSTORE_PATH, SIGNING_STORE_PASSWORD,
SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD.
```

**根本原因**：GitHub 仓库 `p8735489-prog/Localis` 中未配置 4 个必需的 Actions Secrets，导致 CI 环境下签名凭证全部为空，`build.gradle.kts` 在检测到 `CI=true` 时抛出异常终止构建。

构建日志关键证据（步骤 9 "Write signing keystore"）：
```
KEYSTORE_B64:                          <-- 空
No SIGNING_KEYSTORE_B64 secret found — APK will be unsigned
```

## 密钥库信息

从 `build.sh` 和 `keytool` 验证获得：

| 项目 | 值 |
|---|---|
| Keystore 密码 | `localis2024` |
| Key 别名 | `localis` |
| Key 密码 | `localis2024` |
| 证书所有者 | CN=Localis, O=Localis, C=CN |
| 有效期 | 2026-08-17 至 2054-01-02 |
| SHA-256 | 2D:3F:8C:24:D0:F2:65:07:52:AB:2E:4F:F4:58:9A:3A:42:15:90:5B:9B:4D:C0:8D:4E:63:D7:46:42:BE:B0:95 |

## 修复步骤

### 第 1 步：获取 Base64 编码

文件 `keystore_base64.txt` 已生成，内容为 keystore 的 Base64 编码（约 3500 字符）。

你也可以在本地自行生成：

```bash
base64 -w 0 localis-release.jks
```

### 第 2 步：配置 GitHub Secrets

打开仓库页面：`https://github.com/p8735489-prog/Localis/settings/secrets/actions`

点击 **New repository secret**，依次添加以下 4 个 Secret：

| Secret 名称 | 值 |
|---|---|
| `SIGNING_KEYSTORE_B64` | 粘贴 `keystore_base64.txt` 的全部内容 |
| `SIGNING_STORE_PASSWORD` | `localis2024` |
| `SIGNING_KEY_ALIAS` | `localis` |
| `SIGNING_KEY_PASSWORD` | `localis2024` |

### 第 3 步（可选）：使用 gh CLI 一键配置

如果你本地安装了 GitHub CLI 并已认证，可以运行 `setup_github_secrets.sh` 脚本自动配置：

```bash
chmod +x setup_github_secrets.sh
./setup_github_secrets.sh
```

### 第 4 步：确认 workflow 已更新

当前 zip 中的 `.github/workflows/android-release.yml` 已包含以下改进（相比失败的旧版本）：

1. **新增 "Validate release secrets" 步骤**（第 58-68 行）——在构建前验证所有 4 个 Secret 是否存在，缺失时立即报错退出，而非等到 Gradle 构建阶段才失败
2. **改进 "Restore signing keystore" 步骤**（第 70-75 行）——使用 `printf` + `base64 --decode` 解码密钥库到临时文件，并验证文件非空
3. **构建步骤重命名为 "Build signed Release APK"**（第 86-93 行）——明确语义

确保这些改动已推送到 `main` 分支。

### 第 5 步：重新触发构建

方式一——推送新 tag：
```bash
git tag v2.1.0
git push origin v2.1.0
```

方式二——手动触发（workflow 已配置 `workflow_dispatch`）：
在 GitHub 仓库的 Actions 页面选择 "Build Release APK" → 点击 "Run workflow"。

## 验证

构建成功后，workflow 会：
1. 用 `apksigner verify` 验证 APK 签名
2. 用 `apkanalyzer` 检查 application-id 和版本号
3. 上传 `Localis-2.1.0-release.apk` 作为构建产物
4. 创建 GitHub Release 并附带 APK 和 mapping.txt

## 安全提醒

- 密钥库文件 `localis-release.jks` 已在 `.gitignore` 中，**不要提交到仓库**
- GitHub Secrets 配置后不可再次查看值，只能更新或删除
- 如果密钥库曾泄露到公开仓库，请重新生成新密钥库（参见 `SIGNING.md` 第 4 节）
