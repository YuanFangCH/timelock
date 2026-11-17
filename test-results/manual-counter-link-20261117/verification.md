# 手动组数计时联动与 Android 15 模拟器验证

任务标识：`manual-counter-link-20261117`。

## 环境

- AVD：`timelock_recovery_35`，Android 15 / API 35，Pixel 6 配置，设备 ID `emulator-5554`。
- SDK、AVD 镜像、AVD 数据、Gradle 缓存和临时目录均设在 `D:\Dsk\timelock\.local`。启动时同时设置 `ANDROID_USER_HOME`、`ANDROID_EMULATOR_HOME`、`ANDROID_SDK_HOME`，避免模拟器将运行配置写入 C 盘。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
- Release APK：`dist/TimeLock-0.1.0-preview.7.apk`，versionCode 8，versionName `0.1.0-preview.7`。
- Release APK SHA-256：`8f31ac2bfcee5a92e26a3a92c04a78bc48c6ece0185b1ef16f9ed1d9a74feba2`。
- 新预览证书 SHA-256：`9a494a127386d7c529d1d50acce6740b044f6739143e419c602d218e5c525fe4`。

## 验证结果

| 检查 | 结果 | 说明 |
|---|---|---|
| Debug 构建 | PASS | `:app:assembleDebug` 成功。 |
| 定向计时单元测试 | PASS | `:app:testDebugUnitTest --tests dev.timelock.IntervalTimerTest` 成功。 |
| Release 构建与 lint | PASS | `scripts/build-release.ps1` 构建 preview.7 成功。 |
| Release APK 签名与哈希 | PASS | `apksigner verify` 成功，SHA-256 与发布校验文件一致。 |
| AVD 启动和 Debug 安装 | PASS | 模拟器设备显示为 `emulator-5554`，Debug 应用启动并显示组数按钮和新开关文案。卸载 AVD 内原 preview.4 后安装 Debug 包；仅清除模拟器中该应用数据。 |
| Release 安装和启动 | PASS | 从模拟器中卸载 Debug 包后安装 preview.7，确认 versionCode 8、启动成功。 |
| 空闲加一自动启动 | PASS | 加一后组数为 1，服务进入运行，倒计时从 30 秒开始。首次通知授权在启动时完成。 |
| 到期触发后减一重启 | PASS | 30 秒间隔累计到 1 次后按减一，组数变 0、倒计时重新开始，累计显示 0 次。 |
| Release 包归零操作 | PASS | preview.7 Release 在模拟器运行时按归零，组数归 0，计时保持运行且累计触发为 0。 |
| 暂停后减一恢复 | PASS | 暂停后按减一，服务恢复运行，界面显示完整间隔和 0 次。 |
| 加一、归零以及关闭关联的全状态验证 | NOT RUN | 未覆盖每种按钮在运行中、暂停、空闲、停止状态下的完整组合。 |
| 模拟器息屏和长期后台提醒 | NOT RUN | 本次只核验前台交互与一次提醒触发。 |
| 旧签名覆盖安装 | NOT SUPPORTED | 原签名密钥无法恢复。本版使用新证书；preview.1 至 preview.6 用户需先卸载旧版，设置将丢失。 |
| GitHub 发布 | PENDING | 源码推送和 GitHub Release 尚待完成。 |

## 构建命令

在 PowerShell 设置 `JAVA_HOME` 为本机 JDK 17，将 `ANDROID_HOME`、`ANDROID_SDK_ROOT`、`ANDROID_AVD_HOME`、`ANDROID_USER_HOME`、`ANDROID_EMULATOR_HOME`、`ANDROID_SDK_HOME`、`GRADLE_USER_HOME`、`TEMP` 和 `TMP` 指向项目 D 盘 `.local` 对应目录后执行：

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest --tests dev.timelock.IntervalTimerTest
```