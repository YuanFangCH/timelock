# GitHub 可借鉴项目核查

观察日期：2026-10-24。范围：官方仓库 README、实际源码、Manifest、许可证和发布提交。没有安装或运行候选应用，未验证任何候选在荣耀 90 上的可靠性。

## 结论

建议保留本项目的小型 Kotlin 原生实现：借鉴简单项目的交互和短音管理，以及 Fossify 的前台服务和清理资源的职责划分。下列项目均不能直接作为已验证的“每 30 秒锁屏响一次”解决方案。首版无需引入任何候选作为库依赖。

| 项目 | 对本需求的价值 | 源码核查发现 | 采用方式 |
| --- | --- | --- | --- |
| [randombits-dev/interval-timer](https://github.com/randombits-dev/interval-timer) | Kotlin、简洁的循环计时、暂停/继续、短音、保存设置 | CountDownTimer；SoundPool 单流；主 Manifest 无 Service 和 WAKE_LOCK | 首选轻量功能参考；后台和精度逻辑自行实现 |
| [FossifyOrg/Clock](https://github.com/FossifyOrg/Clock) | Kotlin、前台服务、通知、闹钟音频生命周期 | TimerService 声明 specialUse；AlarmService 使用 USAGE_ALARM 并清理播放器 | 前台服务及资源生命周期参考；不整体移植 |
| [NoeSturm/interval-bell](https://github.com/NoeSturm/interval-bell) | 功能最接近：设间隔、倒计时、试听、自动循环 | Activity 内 CountDownTimer；Manifest 没有前台 Service；标准许可证未确认 | 仅交互参考，不能以其后台功能描述代替实测 |

表格依据见下列文件核查；取舍为本项目的工程判断。

## 1. randombits-dev/interval-timer

### 已核查的代码

- [MainActivity.kt](https://github.com/randombits-dev/interval-timer/blob/main/app/src/main/java/dev/randombits/intervaltimer/MainActivity.kt)：SoundPool 限制一个流；用播放 ID 停音并防止重复播放；SharedPreferences 保存设置。其声音属性为 USAGE_MEDIA，本项目拟采用 USAGE_ALARM，不能原样照搬音量行为。
- [HiitTimer.kt](https://github.com/randombits-dev/interval-timer/blob/main/app/src/main/java/dev/randombits/intervaltimer/HiitTimer.kt)：准备/运动/休息三种阶段，使用 CountDownTimer 并提供暂停、继续和停止。没有显式的绝对节拍推进实现；不适合直接复用为本项目的单一无限循环内核。
- [TimerFragment.kt](https://github.com/randombits-dev/interval-timer/blob/main/app/src/main/java/dev/randombits/intervaltimer/TimerFragment.kt)：前台添加 FLAG_KEEP_SCREEN_ON，onPause 清除该标志并保留计时。这不是息屏 CPU 唤醒锁，也不是前台服务。
- [AndroidManifest.xml](https://github.com/randombits-dev/interval-timer/blob/main/app/src/main/AndroidManifest.xml)：本次读取的主清单没有 Service、前台服务权限或 WAKE_LOCK。由此只能确认主工程未声明这些能力，不能宣称锁屏稳定。
- [app/build.gradle.kts](https://github.com/randombits-dev/interval-timer/blob/main/app/build.gradle.kts)：minSdk 24，targetSdk/compileSdk 35；工程同时有 Compose 插件和传统 Fragment/View 代码。用于功能参考即可，无需复制构建配置。

### 可转化为本项目的事项

1. 保留“试听与正式播放共用一个声音模块”和“播放 ID 可停止”的思路。
2. 保留暂停、继续的清晰操作，但本项目使用截止时间保存剩余时间。
3. 音频加载完成前禁止开始或试听；播放失败应给出状态，而不是只重置播放标记。
4. 状态、计时、播放由 Service 统一管理，页面退出后不依赖 Activity/Fragment 存活。

### 许可证与维护证据

- [LICENSE](https://github.com/randombits-dev/interval-timer/blob/main/LICENSE) 为 MIT，版权标记为 Matthew Nienow。若实际复制或改编实质代码，应保留其版权和许可文本，并记录引入文件与修改。
- [发布页](https://github.com/randombits-dev/interval-timer/releases) 本次显示 v1.2.0，关联提交 [b7cf63b](https://github.com/randombits-dev/interval-timer/commit/b7cf63b)；提交内容为版本更新和变更记录。v1.1.3 发布说明提到允许后台继续，与上述 onPause 行为相符，不能推导出 Doze 下稳定。
- 仓库页面显示 57 次提交。本轮没有取得默认分支最新提交的完整 SHA 与日期，不将发布标签当作最新 HEAD，不判断未来维护承诺。

## 2. FossifyOrg/Clock

### 已核查的代码

- [AndroidManifest.xml](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/AndroidManifest.xml)：TimerService、AlarmService、StopwatchService 使用 specialUse，并提供用途属性；同时声明通知、唤醒锁和精确闹钟等能力。本项目只选自身实际需要的能力，不引入全屏闹钟、悬浮窗和重启恢复。
- [TimerService.kt](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/kotlin/org/fossify/clock/services/TimerService.kt)：负责持续通知与计时状态展示，使用 START_NOT_STICKY；通知通道关闭声音，避免状态更新响铃。该文件未展示实际短周期调度或获取唤醒锁，因此不能仅凭此文件证明短周期锁屏可靠性。
- [AlarmService.kt](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/kotlin/org/fossify/clock/services/AlarmService.kt)：MediaPlayer 使用 USAGE_ALARM/CONTENT_TYPE_SONIFICATION；停止/销毁时停止并释放播放器，移除回调和振动。其设计是持续闹钟、可渐增系统音量，本项目采用一次性短音且不修改系统音量。
- [TimerHelper.kt](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/kotlin/org/fossify/clock/helpers/TimerHelper.kt)：数据库 CRUD。本项目仅记住一个间隔，不需要这层数据库。

### 可转化为本项目的事项

1. 运行通知为静音状态通道，真正提醒声由声音模块控制，防止每秒更新通知产生噪音。
2. 先正确进入前台，再维持计时；通知、声音和唤醒锁的失败要向页面反馈。
3. 所有停止路径集中清理：取消回调、停止声音、释放焦点/播放器/锁、移除通知、stopSelf。
4. 初次开始必须由用户在前台操作触发，通知按钮按当前会话处理，不从过期 Intent 复活旧任务。

### 许可证与维护证据

- [LICENSE](https://github.com/FossifyOrg/Clock/blob/main/LICENSE) 为 GPL v3。实际复制并形成受 GPL 约束的派生程序后，对外分发需要履行相应许可、修改标记及对应源码提供要求（尤其 §§4–6）；采用普通平台 API 的独立实现应与复制代码区分。本计划采用架构观察与独立编写，暂未导入源码或资源。
- [发布页](https://github.com/FossifyOrg/Clock/releases) 本次可见 1.6.0，关联提交 [4ecb9e3](https://github.com/FossifyOrg/Clock/commit/4ecb9e3)；该提交的 CHANGELOG 明确标记版本日期 2026-01-30。仓库页面显示 2,349 次提交及开放的 Issue/PR，这些只作为可见维护记录，不能推断具体手机兼容性或响应速度。
- 未取得本轮默认分支 HEAD 的完整 SHA 与最新日期；本报告读取的 main 源文件可能包含发布后变化。若未来复制代码，必须另行固定 SHA 并核对当时许可。

## 3. NoeSturm/interval-bell

### 已核查的代码

- [README](https://github.com/NoeSturm/interval-bell/blob/main/README.md)：输入间隔、试听、倒计时和自动循环的产品流程接近需求。可参考界面分区；首版用三个快捷按钮即可，不需要它的多种音色和录音功能。
- [MainActivity.java](https://github.com/NoeSturm/interval-bell/blob/main/app/src/main/java/com/intervalbell/app/MainActivity.java)：在 Activity 内创建 CountDownTimer，onFinish 播放后递归启动下一轮；调用 ToneSynthesizer 和 AudioRecorder。源码实现已不同于 README 所述的简单 MediaPlayer/系统提示音路径，应以源码为准。
- [AndroidManifest.xml](https://github.com/NoeSturm/interval-bell/blob/main/app/src/main/AndroidManifest.xml)：声明 WAKE_LOCK、振动和录音；未声明 Service。检查的 MainActivity 没有获取/释放部分唤醒锁的实现。声明权限本身不能保证设备保持唤醒。

### 不直接复用的理由

1. 生命周期依赖 Activity，没有前台 Service 支撑；连续 CountDownTimer 重启也没有本项目要求的显式绝对节拍处理。
2. 录音权限与多音色合成超出本项目当前范围。
3. 仓库根目录未见标准 LICENSE，README 仅笼统表示可修改/分发，缺乏明确完整条款。当前只作界面/需求观察，不复制其源码、图标和音频。

### 维护证据

仓库页面本次显示 23 次提交，但 GitHub Activity/提交列表未返回可用的最新时间与提交 SHA，因此维护状态标为“未确认”。不依据星数、搜索引擎抓取时间或 README 愿景判断活跃度。

## 4. 对实施计划的具体补充

| 要落地的设计 | 借鉴来源或教训 | 本项目执行方式 |
| --- | --- | --- |
| 计时与界面分离 | 两个轻量候选均依赖页面生命周期 | Service 持有唯一计时；页面只订阅快照 |
| 锁屏运行要有直接证据 | WAKE_LOCK 权限声明和后台继续描述均不足 | 同时记录前台状态、真实锁屏、Doze、锁持有/释放以及音频证据 |
| 提示音不叠加且能立即停止 | randombits 单流/播放 ID | 一个声音实例；停止/暂停路径统一清理 |
| 通知更新保持安静 | Fossify TimerService 的静音通道 | 一个低打扰状态通知；循环声走独立模块 |
| 平台版本约束单独处理 | 候选 targetSdk、清单能力与本项目不一致 | 按 API 33/34/35/36 处理权限、前台服务与音频焦点规则 |
| 复制边界可追溯 | MIT / GPL / 不明确许可并存 | 暂定独立编写；引入外部内容前登记来源、SHA、文件和许可证，声音优先自行生成短音 |

## 5. 研究限制

- 所有技术结论来自本轮读取的源码/清单；不把项目宣传中的“可靠”当作测试结论。
- raw main 链接随上游变化；本轮未取得最新 HEAD，不能把观察结果当作永远成立。正式复用代码前必须固定版本。
- GitHub API 和提交列表部分访问失败；用已读取的发布提交提供有限维护证据，缺失项明确保留为未确认。
- 未 clone、构建或运行上游工程；未复制上游源码、音频、图标或签名材料。
