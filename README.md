# 间隔提醒（TimeLock）

面向荣耀 90 的轻量 Android 应用：设定秒/分钟间隔后播放短提示音并自动循环。应用离线运行，不收集数据。

## 当前预览版

当前预览版为 **0.1.0-preview.4**，支持 30 秒、50 秒、2 分钟快捷选项与自定义间隔，提供倒计时、试听、暂停、继续和停止。内置四种更响的提示音，也可选择手机铃声库里的系统声音并调整媒体音量。运行时显示持续通知，屏幕关闭后继续循环。后台可靠性会受系统省电和厂商策略影响。详见[预览版说明](docs/release-notes/0.1.0-preview.4.md)。

从 [0.1.0-preview.4 发布页](https://github.com/YuanFangCH/timelock/releases/tag/v0.1.0-preview.4) 下载 APK 和 SHA-256 文件；安装方法见[安装说明](docs/install.md)。源码采用 MIT 许可证，见 [LICENSE](LICENSE)。

## 0.1.0-preview.4 媒体通道修复

- 提醒声与试听统一使用媒体通道，跟随系统当前媒体输出（如蓝牙耳机）；音量检查和界面音量键同步使用媒体音量。媒体音量为零时跳过播放，闹钟音量不再控制提醒声。
- 蓝牙实际可听性、运行中连接/断开耳机及厂商后台行为待真机验收。验证记录：`test-results/bluetooth-media-20261029/verification.md`。

## 技术与构建

- Kotlin 原生 Android，包名 `dev.timelock`，最低系统声明 Android 10 / API 29，compile/target SDK 36。
- Gradle 9.3.1 / Android Gradle Plugin 9.1.0，JDK 17；安装 SDK 后使用：

```powershell
.\gradlew.bat :app:assembleDebug
```

若 JDK 未在 `PATH` 中，先在当前 PowerShell 窗口设置 `$env:JAVA_HOME`。

首发源码在 Android 15 / API 35 Pixel 配置的 AVD 上完成 8 项计时相关单元测试及 30 秒、50 秒、2 分钟间隔的短时息屏验证；preview.3 的声音选项和系统铃声息屏提醒已通过模拟器检查。preview.4 的定向验证见上方记录。荣耀 90 真机、深度 Doze、实际锁屏可听性和耗电表现待验收，不以 AVD 结果代替真机结论。

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
