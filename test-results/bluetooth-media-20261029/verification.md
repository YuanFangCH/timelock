# preview.4 媒体通道修复与发布验收

日期：2026-10-29。结果：正式签名构建、覆盖升级和本次声音/计时定向验收通过；物理蓝牙未测试。

## 环境与故障恢复

原 E 盘 Android/Gradle 文件读取报“指定不存在的设备”，Gradle JAR ZIP 中央目录不可读。Windows System 日志存在 disk 51、NTFS 50/140，包含 E:\$Mft 延迟写入失败；这不是已修好的硬件故障。日志见 disk-errors.txt。停止使用原 E 盘工具和 Gradle daemon，在项目忽略的 .local/ 下从官方源安装独立 SDK、Gradle 9.3.1（SHA-256 与官方校验文件相同）及 Android 15 Google APIs x86_64 镜像。

原 aitext_test 数据位于故障 E 盘，未覆盖或清空。改用 D 盘独立 AVD timelock_recovery_35；实际 ADB serial=emulator-5554，emu avd name 匹配，ro.kernel.qemu=1、sys.boot_completed=1、API=35。所有设备命令显式指定此 serial；未操作真机或使用 connected*。

构建环境：JAVA_HOME=C:\Program Files\Java\jdk-17.0.2，ANDROID_HOME/ANDROID_SDK_ROOT=<repo>/.local/android-sdk，GRADLE_USER_HOME=<repo>/.local/gradle-home。启动 AVD 另设 ANDROID_AVD_HOME=<repo>/.local/avd、ANDROID_USER_HOME=<repo>/.local/android-user。恢复脚本在 tests/agent-temp/bluetooth-media-20261029/，启动日志 emulator-stdout.log / emulator-stderr.log。

## 构建、签名与安装

- 执行 scripts/build-release.ps1，内部仅 :app:assembleRelease（含 lintVitalRelease），BUILD SUCCESSFUL；日志 release-build.log。未重新生成项目正式密钥。
- versionName=0.1.0-preview.4，versionCode=5。
- APK SHA-256：e3fd5b6b3ca66c2929d6998c2ca4cf770173f9f13b8a840bf8063e2a2ec75c37。
- 从 GitHub 下载 preview.3，apksigner verify --print-certs 对比两版证书 SHA-256 一致：24c9e696c832c17355aa118c0b3ef0cb032c145b7c2dacc0adb597af1a565c9c。证据 preview3-signature.txt / preview4-signature.txt。
- 在指定 AVD 先安装 preview.3，再 adb -s emulator-5554 install -r dist/TimeLock-0.1.0-preview.4.apk，成功覆盖；dumpsys package 确认为 versionCode=5。

## 定向验收

临时脚本位于 tests/agent-temp/bluetooth-media-20261029/，Python 调用每条 ADB 均绑定同一 AVD；UI 通过 uiautomator 回读控件与状态，并结合 TimeLock 事件及 dumpsys audio 验证。日志只证明 Android 接受/路由播放，不证明物理设备可听性。

| 项目 | 结果与证据 |
| --- | --- |
| 四种内置音试听、3 秒定时提醒、停止 | 4/4 通过；acceptance-run.log 的 source=builtin:0..3、preview/audio_request_accepted、session_stopped；acceptance-events.log |
| 内置音媒体属性 | SoundPool=USAGE_MEDIA；builtin-0-audio.txt 至 builtin-3-audio.txt |
| 媒体音量 0、闹钟音量 5 | 试听与定时播放均 reason=media_volume_zero；volume-system.log 前五项、volume-helper.log、media-zero.xml/png |
| 闹钟静音、媒体音量 8 | 试听与定时播放均接受；alarm-system.log 前三项、alarm-muted-audio.txt，STREAM_ALARM Muted=true、STREAM_MUSIC Muted=false |
| 音量键 | settings-picker.log / volume-key-audio.txt 确认硬件按键请求 sugg:STREAM_MUSIC；模拟器注入按键没有观察到数值增量，只把路由计为通过，真实按键调节幅度待真机确认 |
| 声音设置入口 | 成功进入 Android Settings 并返回；sound-settings-activity.txt、sound-settings.png |
| 系统铃声 | 选择 Pixel Sounds / Drip，试听和定时提醒通过；system-selected.xml/png、system-final.log |
| 系统铃声媒体属性 | 实际 started MediaPlayer=USAGE_MEDIA，system-active-audio.txt / system-attributes.log |
| 息屏、恢复、暂停、继续、停止 | 3 秒间隔息屏 10 秒内至少两次接受播放，约 2.5 秒自动停止；恢复页面显示暂停按钮；暂停 4 秒无播放，继续恢复播放，停止后 4 秒无播放；system-final.log、running-restored.xml/png、stopped.xml/png |
| 选项保存 | 强停重开仍选择 Drip；system-final.log |
| 页面 | preview4-final.xml/png 显示媒体音量说明 |
| 差异检查 | git diff --check 通过 |

## 测试工具限制与早期失败

Android 15 的 cmd media_session --set 对本模拟器没有实际改变音量；早期脚本中“预期静音”断言失败属于测试前置条件未建立，不记作产品通过。旧版 baseline.log 中关于媒体 0 的判断同样无效；preview3-baseline-audio.txt 只用于证明旧版确实 USAGE_ALARM。随后用独立临时 APK（仅模拟器、MODIFY_AUDIO_SETTINGS 权限）设音量并回读，确认媒体 0 和闹钟 5 后重新验收。

该镜像闹钟音量最小为 1，不能直接设 0；普通音量 mute 也没有静音闹钟。闹钟静音场景因此改用临时工具授权勿扰策略，禁止闹钟、允许媒体，并由 dumpsys audio 证实实际闹钟已静音后验收。没有把无法建立的“闹钟音量数值 0”计为通过。早期 alarm-system.py 中数值必须为 9 的按键断言失败，后以系统音量请求的目标流验证路由，数值增量保留未验证。

## 未执行与未覆盖

未执行全量测试；本次没有新增或重跑计时核心单元测试，首发 8 项历史结果不冒充本次结果。真机蓝牙、连接/断开切换、实际扬声器或耳机听音、MagicOS、深度 Doze、PIN 锁屏和耗电均未验证。

## 发布交付

签名 APK：dist/TimeLock-0.1.0-preview.4.apk；校验：dist/SHA256SUMS.txt。发布目标为 GitHub v0.1.0-preview.4 非草稿预发布，随附两文件；发布后独立回下载校验记录见 release-verification.json（本机结果目录）。
