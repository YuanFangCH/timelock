# 主界面和应用图标验证

日期为 2026-11-19。使用本地 SDK 36、JDK 17 和 Android 15 / API 35 的 `timelock_recovery_35` 模拟器。

| 检查 | 结果 |
| --- | --- |
| Debug 构建 | 通过。生成 `app/build/outputs/apk/debug/app-debug.apk`。初次构建访问默认调试签名目录失败，使用项目内的 `ANDROID_USER_HOME` 后成功。 |
| 模拟器安装 | 通过。在只读 AVD 会话中覆盖已安装的 Debug 包。 |
| 主界面和设置区 | 通过。检查了白底、浅蓝色倒计时面板、细分隔线、小圆角按钮及滚动时的系统栏边界。截图为本地的 `home.png` 和 `settings.png`。 |
| 启动图标 | 通过。应用列表显示蓝底白色秒表，点击可进入主界面。本地截图为 `launcher.png`。 |
| 小屏和放大字体 | 通过。在 320dp 宽度、字体缩放 1.6 下输入 1440 分钟，倒计时完整显示 `24:00:00`，主按钮文字可读。本地截图为 `small-large-font.png`。 |
| 计时和设置 | 通过。切换秒和分钟，确认选中状态。三个快捷间隔正确更新倒计时，运行期间快捷按钮禁用。开始、暂停、继续和停止均更新状态。 |
| 手动组数 | 通过。关联开启时，加一、减一、归零均更新组数，操作后计时保持运行。加一后确认累计触发显示为零。 |
| 提示音试听 | 通过。点击后服务记录 `event=preview_accepted sessionId=1 source=builtin:0`。无音频输出的模拟器会话只验证播放请求。 |
| preview.8 Release | 通过。`scripts/build-release.ps1` 成功完成 Release 构建和 lint vital。APK 声明版本号 `0.1.0-preview.8`、versionCode `9`。签名与 preview.7 相同，Android 15 模拟器覆盖安装后启动成功。 |
| 未执行 | 荣耀 90 真机验收、主题图标实际着色、锁屏和后台可靠性、全量测试。 |

构建时将 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 指向 `.local/android-sdk`，`ANDROID_USER_HOME` 指向 `.local/android-user`，`GRADLE_USER_HOME` 指向 `.local/gradle-home`，`TEMP` 和 `TMP` 指向 `.local`。

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug --offline
.local/android-sdk/platform-tools/adb.exe -s emulator-5556 install -r -g app/build/outputs/apk/debug/app-debug.apk
.local/android-sdk/platform-tools/adb.exe -s emulator-5556 shell am start -W -n dev.timelock/.MainActivity
pwsh -NoProfile -File tests/agent-temp/ui-refresh/check-ui.ps1
git diff --check
```

使用 `apksigner verify --print-certs` 核验两个预览包的证书，使用 `aapt2 dump badging` 核验新包版本，再通过 `adb install -r` 从 preview.7 覆盖安装。preview.8 APK 的 SHA-256 为 `eb68086fa068f6b58a68aec8888100cec2692731b13f6a97048125e52e085a0a`。
