# 声记 VoiceMin

> 一个 Android 本地离线录音转写工具：边说边出字、带时间戳、可回放，并支持把多段课堂录音交给 AI 提炼重点与作业安排。
>
> An offline on-device speech-to-text recorder for Android, with live transcript, timestamps, playback, and batch AI summary via your own DeepSeek API key.

> **非官方项目**：本项目与 DeepSeek、华为、小米、阿里等公司均无隶属或合作关系，仅为个人独立开发的学习项目。

---

## 功能

- **本地离线转写**：sherpa-onnx + SenseVoice（INT8），音频不出手机，不按分钟计费。
- **实时出字（按句）**：silero VAD 检测停顿，每说完一句约 0.4 秒出字，带 `[分:秒]` 时间戳。
- **点句回放**：点任意一句，从该句时间点播放（AudioTrack 直播 PCM）。
- **后台 / 熄屏继续录音**：前台服务 + 唤醒锁，通知栏可停止。
- **问 AI**：把当前转写（或长按某句）发给 DeepSeek 提问，录音不中断；需自备 API Key（BYOK）。
- **上传图片 / PDF**：识别成文字，作为提问参考（走 DeepSeek 视觉，前 8 页）。
- **录音库**：文件夹 / 子文件夹、重命名、收藏、移动、查看转写。
- **多选导出 / AI 批量总结**：勾选多段录音，合并导出 `.md`，或让 AI 提炼「老师强调的重点 + 作业/实验安排」。
- **电气热词纠正**：内置断路器/继电保护/PLC/SCADA/矢量控制/变频器/互感器/整定值/Modbus/IEC 61850 等映射，可编辑。
- **20 条上限 + 收藏豁免**：未收藏的自动清理最旧，收藏的永久保留。
- **数据本地**：录音、转写、问答均存应用私有目录，支持导出 TXT/Markdown、一键清空。

---

## 技术栈

- Android（Java，原生，无第三方 UI 框架）
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 2.0.1（Apache-2.0）+ SenseVoice INT8
- [silero-vad](https://github.com/snakers4/silero-vad)（MIT）
- DeepSeek 开放平台 API（BYOK）

---

## 环境要求

- Android 7.0+（API 24+）；HarmonyOS 4.2 的安卓兼容层可运行
- JDK 17
- Android SDK（platform 34 + build-tools 35）
- Windows PowerShell 5.1+（脚本用 PowerShell 编写）
- Python 3（组装 APK 用；确保 `python` 在 PATH，或设置环境变量 `VOICEMIN_PYTHON` 指向 python.exe）
- Windows 10+ 自带的 `tar`（解压 AAR / 模型）

---

## 构建

```powershell
# 1) 下载依赖（AAR、模型、kotlin-stdlib、VAD 模型），约 400 MB
powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1

# 2) 打包（需要先设置 ANDROID_HOME / JAVA_HOME，或用 -Sdk / -Jdk 参数）
#    Python 不在 PATH 时，先设置：$env:VOICEMIN_PYTHON = "C:\path\to\python.exe"
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Sdk "D:\Android\android-sdk" -Jdk "D:\Android\openjdk\jdk-17.0.12"

# 产物：build\app-release.apk
```

依赖与产物不入库（见 `.gitignore`），用 `setup.ps1` 重新获取即可。

---

## 使用

1. 安装 APK，允许麦克风权限。
2. （可选）主界面右上角「设置」→ 设置启动页图片 / 恢复默认 / 关闭。
3. 长按「问 AI」填入你自己的 DeepSeek API Key（只存本机）。
4. 点「开始录音」，边说边出字；停止后点句子可回放。
5. 「录音库」里整理、收藏、多选导出或 AI 总结。

> 后台录音需要在系统里放行：设置 → 电池 → 应用启动管理 → 声记 → 手动管理（允许后台活动/自启动），并允许通知。

---

## 隐私

- 录音、转写、问答记录只存在手机应用私有目录，不上传。
- 只有你主动点「问 AI」「AI总结」「上传图片/PDF」时，相关内容才会经 HTTPS 发送到 DeepSeek 开放平台。
- API Key 只保存在本机 SharedPreferences。

---

## 免责声明

- 本项目为非官方个人项目，与 DeepSeek / 华为等无隶属或合作关系。
- AI 生成内容可能有误，仅供参考，不构成任何专业建议。
- 请勿用于侵犯他人隐私、著作权或任何违法违规用途。
- 使用本软件产生的任何后果由使用者自行承担。

---

## 许可证

- 本仓库代码：MIT（见 `LICENSE`）。
- 第三方组件与模型（保留署名；许可证原文见 `docs/FunASR_MODEL_LICENSE.txt`）：
  - sherpa-onnx：Apache-2.0
  - silero-vad：MIT
  - SenseVoice（`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`，阿里通义实验室）：FunASR Model License v1.1，保留模型名称与出处
  - kotlin-stdlib：Apache-2.0
