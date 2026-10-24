# 间隔提醒（TimeLock）

面向荣耀 90 的轻量 Android 应用：设定秒/分钟间隔后播放短提示音并自动循环。应用离线运行，不收集数据。

## 首次预览版

首个预览版为 **0.1.0-preview.1**，支持 30 秒、50 秒、2 分钟快捷选项与自定义间隔，提供倒计时、试听、暂停、继续和停止。开始运行时显示持续通知，用户可在通知栏控制；屏幕关闭后继续运行。后台可靠性会受系统省电/厂商限制影响。详见[预览版说明](docs/release-notes/0.1.0-preview.1.md)。

从 [GitHub Releases](https://github.com/YuanFangCH/timelock/releases/latest) 下载 APK 和 SHA-256 文件；安装方法见[安装说明](docs/install.md)。源码采用 MIT 许可证，见 [LICENSE](LICENSE)。

## 技术与构建

- Kotlin 原生 Android，包名 `dev.timelock`，最低系统声明 Android 10 / API 29，compile/target SDK 36。
- Gradle 9.3.1 / Android Gradle Plugin 9.1.0，JDK 17；安装 SDK 后使用：

```powershell
.\gradlew.bat :app:assembleDebug
```

若 JDK 未在 `PATH` 中，先在当前 PowerShell 窗口设置 `$env:JAVA_HOME`。

首发源码在 Android 15 / API 35 Pixel 配置的 AVD 上完成 8 项计时相关单元测试及三个提醒间隔的短时息屏验证。签名预览 APK 也已安装启动。荣耀 90 真机、深度 Doze、锁屏音频可听性和耗电表现待验收，不以 AVD 结果代替真机结论。

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
