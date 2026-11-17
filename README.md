# 间隔提醒（TimeLock）

面向荣耀 90 的轻量 Android 应用：设定秒/分钟间隔后播放短提示音并自动循环。应用离线运行，不收集数据。

## 当前预览版

当前预览版 **0.1.0-preview.7** 增加手动组数联动行为：加一、减一、归零都会重启计时并清零累计触发，计时未开始或已停止时按当前设置启动。此版本使用新的预览签名证书；从 preview.1 至 preview.6 升级需先卸载旧版，应用内设置会被清除。安装说明见 [docs/install.md](docs/install.md)，验证记录见 [test-results/manual-counter-link-20261117/verification.md](test-results/manual-counter-link-20261117/verification.md)。

preview.7 延续计时器的快捷间隔、自定义间隔、试听、暂停、继续、停止、系统铃声选择和持续通知功能。提示音使用媒体音量并跟随当前媒体输出；后台可靠性会受系统省电和厂商策略影响。详见 [preview.7 发布说明](docs/release-notes/0.1.0-preview.7.md)。

从 [0.1.0-preview.7 发布页](https://github.com/YuanFangCH/timelock/releases/tag/v0.1.0-preview.7) 下载 APK 和 SHA-256 文件；源码采用 MIT 许可证，见 [LICENSE](LICENSE)。

## 手动组数计数器

- 主页面加一、减一和归零会在关联开关开启时重启计时并清零累计触发；未运行时按当前设定启动，关闭时只改组数。
- preview.7 的定向构建和模拟器交互记录见 `test-results/manual-counter-link-20261117/verification.md`。

## 技术与构建

- Kotlin 原生 Android，包名 `dev.timelock`，最低系统声明 Android 10 / API 29，compile/target SDK 36。
- Gradle 9.3.1 / Android Gradle Plugin 9.1.0，JDK 17；安装 SDK 后使用：

```powershell
.\gradlew.bat :app:assembleDebug
```

若 JDK 未在 `PATH` 中，先在当前 PowerShell 窗口设置 `$env:JAVA_HOME`。

preview.7 的 Debug/Release 构建、定向计时测试、Release 签名及 Android 15 AVD 交互通过。模拟器验证详情与尚未覆盖项目见 `test-results/manual-counter-link-20261117/verification.md`。荣耀 90 真机的锁屏可听性和厂商后台行为仍待验收。

本机 E 盘于 2026-10-29 出现磁盘 I/O 与 NTFS 写入错误；原 SDK、AVD 和全局 Gradle 缓存不可作为可靠运行环境。本次恢复工具位于忽略的 `.local/`，构建时设置 `ANDROID_HOME` 与 `ANDROID_SDK_ROOT` 为项目 `.local/android-sdk`、`GRADLE_USER_HOME` 为 `.local/gradle-home`，JDK 仍使用本机 JDK 17。独立 AVD 为 `timelock_recovery_35`，AVD 目录为 `.local/avd`，Android 用户目录为 `.local/android-user`；启动时分别设置 `ANDROID_AVD_HOME`、`ANDROID_USER_HOME`。不要把工具、镜像或签名提交到仓库。

正式签名密钥存于本机 Windows 用户目录的 `LOCALAPPDATA\TimeLock\Signing`，构建脚本用当前 Windows 用户的 DPAPI 解密保存的密码。请保管签名备份；私钥和密码均不可放入仓库、日志或发布附件。首次生成签名并构建预览 APK：

```powershell
$env:JAVA_HOME = '<JDK 17 installation path>'
.\scripts\build-release.ps1 -InitializeSigningKey
```

后续发布在同一 Windows 用户下运行 scripts/build-release.ps1；脚本仅在本机签名，不上传密钥。

## 项目文件

- `app/`：Android 应用源码、界面、计时服务和定向单元测试。
- `docs/implementation-plan.md`：完整设计与阶段计划。
- `docs/research/github-references.md`：开源项目源码和许可证对照。
- `docs/install.md`：侧载、通知和后台设置说明。
- `docs/release-notes/`：对外预览版说明。
- `scripts/build-release.ps1`：本机创建签名并构建 APK。
- `test-results/s0-s1-preview/verification.md`：首发验证结论。
- `test-results/s0-s2-sounds/verification.md`：提示音电平、系统铃声库与试听验证。
