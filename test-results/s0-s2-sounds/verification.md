# 0.1.0-preview.3 提示音验证

日期：2026-10-24。设备：Android 15 / API 35 Pixel 配置 AVD。

## 源音频电平

| 声音 | 峰值 | RMS | 时长 |
|---|---:|---:|---:|
| 清脆双响 | 83.0% / -1.6 dBFS | 0.488 | 0.540 s |
| 柔和双响 | 78.0% / -2.2 dBFS | 0.480 | 0.660 s |
| 高低提示 | 83.0% / -1.6 dBFS | 0.505 | 0.650 s |
| 三声短响 | 82.0% / -1.7 dBFS | 0.448 | 0.680 s |

旧单音 WAV 峰值是 23.8% / -12.5 dBFS，RMS 为 0.163，时长 0.440 秒。SoundPool 左右声道音量为 1.0。新波形峰值比旧波形提升约 10–11 dB；这个差值衡量源文件，不代表不同手机的主观响度差值。生成/验证命令：

```powershell
node scripts/generate-tones.js
Get-ChildItem app\src\main\res\raw\reminder_*.wav | ForEach-Object {
  node tests\agent-temp\sound-options-20261024\check_pcm_level.js $_.FullName
}
```

执行结果均达到检测脚本的 `peak >= 0.7` 门槛。逐项输出保留于本机的 `test-results/sound-options-20261024/audio-levels.txt`。

## 模拟器检查

| 场景 | 结果 |
|---|---|
| 安装 0.1.0-preview.3 | PASS；versionCode 4；保留原 2 分钟间隔设置 |
| 清脆双响试听 | PASS；系统接受内置 `builtin:0` 播放请求，随后停止 |
| 柔和双响试听 | PASS；系统接受内置 `builtin:1` 播放请求，随后停止 |
| 系统铃声选择器 | PASS；从系统声音类别选取 “Cosmic Play” |
| 系统铃声试听 | PASS；播放路径为 `source=system`，约 2.5 秒后停止 |
| 强停后重开 | PASS；界面仍显示选中的 “Cosmic Play” |
| 屏幕关闭时 30 秒系统铃声提醒 | PASS；`reminder_due` 延迟 5 ms，随后 `source=system`，约 2.5 秒后停止 |
| 声音设置按钮 | PASS；打开 Android “Sound & vibration” 设置页 |
| 定向计时回归 | PASS，8/8；Release `lintVitalRelease` 通过 |

Release 包 SHA-256：`bd721090e766b80bae992d879334a7d52ccadb878ac10b8f85becf7e7fb41bd4`。GitHub Release asset digest 与此值一致。预览签名证书 SHA-256：`24c9e696c832c17355aa118c0b3ef0cb032c145b7c2dacc0adb597af1a565c9c`，与 preview.1/.2 相同。

没有连接荣耀 90。模拟器日志只证明 Android 接受并播放了对应音频流，没有实际扬声器录音或人工听音证据；系统音量页仅打开检查，没有调整音量滑块。
