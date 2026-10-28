# Third-Party Notices / 第三方组件与模型声明

本项目使用了以下第三方组件与模型。分发本项目的 APK 或源码时，请保留本文件及相应版权声明。

## 1. sherpa-onnx

- 项目：https://github.com/k2-fsa/sherpa-onnx
- 版本：2.0.1（Android AAR）
- 许可证：Apache License 2.0
- 用途：离线语音识别推理引擎（含 JNI 原生库）

## 2. SenseVoice（语音识别模型权重）

- 模型：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`
- 来源：https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models （由 FunAudioLLM / 阿里通义实验室发布）
- 许可证：**FunASR Model Open Source License Agreement v1.1**（阿里巴巴集团）
  - 允许使用、复制、修改、分享；
  - **必须注明出处及作者信息，并保留模型名称**；
  - 协议写明「仅作为参考和学习使用」，商业使用前建议向官方确认。
- 原文：见 `docs/FunASR_MODEL_LICENSE.txt`

> 如果你不希望分发模型权重，可改为「首次运行时由用户自行下载模型」，或从本仓库移除模型、仅提供构建说明。

## 3. silero-vad

- 项目：https://github.com/snakers4/silero-vad
- 模型：`silero_vad.onnx`
- 许可证：MIT
- 用途：语音活动检测（VAD），用于按句切分

## 4. kotlin-stdlib

- 包：`org.jetbrains.kotlin:kotlin-stdlib:1.9.24`
- 许可证：Apache License 2.0
- 用途：sherpa-onnx Android 绑定的运行时依赖

## 5. DeepSeek 开放平台 API

- 服务：https://platform.deepseek.com
- 用途：用户自备 API Key（BYOK）调用问答 / 识别
- 本项目不包含、不分发任何 DeepSeek 的密钥或模型权重；「DeepSeek」为其权利人商标，本项目仅作描述性提及，与官方无隶属或合作关系。

---

## 版权归属

- 本项目代码版权归项目作者所有。
- 上述第三方组件与模型的版权归各自权利人所有，使用须遵守其许可证。
