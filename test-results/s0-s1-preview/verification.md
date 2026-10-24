# 0.1.0-preview.1 发布验证摘要

日期：2026-10-24。设备：Android 15 / API 35、x86_64 Pixel 6 配置 AVD。

## 验证结果

| 检查 | 结果 |
|---|---|
| Android debug 构建、安装及界面 | PASS |
| 定向单元测试 | PASS，8/8 |
| 屏幕关闭 30 秒循环 | PASS，10 轮；调度延迟 7–31 ms |
| 屏幕关闭 50 秒循环 | PASS，1 轮；延迟 43 ms |
| 屏幕关闭 2 分钟循环 | PASS，1 轮；延迟 102 ms |
| 暂停、保留剩余时间、继续 | PASS；保留 9.05 秒，继续后正确触发 |
| 停止及资源清理 | PASS；无持续唤醒锁或运行通知 |
| 提示音请求与播放流停止 | PASS；SoundPool 请求被接受并在短音后停止 |
| Release APK 签名、安装及版本 | PASS；签名 DN 为 `CN=TimeLock Preview, O=TimeLock`；Android 15/API 35 AVD 安装启动；versionName `0.1.0-preview.1`，versionCode `2` |
| Release APK SHA-256 | `3f348b967e4021fa5753df71406683f531fcc53bd47cc946525176fbd981d016` |
| Release APK 证书 SHA-256 | `24c9e696c832c17355aa118c0b3ef0cb032c145b7c2dacc0adb597af1a565c9c` |

定向单元测试命令：

```powershell
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest --tests dev.timelock.IntervalTimerTest :app:assembleDebug
.\scripts\build-release.ps1
```

息屏运行通过在模拟器启动后关闭显示屏观察应用事件记录。Release 构建同时通过 Android Gradle Plugin 的 `lintVitalRelease`。测试期间未模拟受 PIN 保护的锁屏，Android 空闲状态为 INACTIVE，未验证深度 Doze 或电池豁免下的行为。音频事件证明 Android 接受了声音流，不证明物理扬声器实际可听。
