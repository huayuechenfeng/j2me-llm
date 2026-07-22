

# J2ME LLM 离线配置包生成器

直接在安卓手机浏览器打开 `index.html` 即可使用，不需要网络，也不会向服务器上传表单内容。填写档案后生成 `J2ME-LLM-config-v2.j2cfg`，再通过系统蓝牙分享给 Java 手机。

## 安全边界

- `.j2cfg` 中的 API 密钥是明文。Base64 不是加密，CRC-32 也只用于检测损坏或意外修改。
- 建议使用有额度限制、可以随时撤销的网关令牌，不要放入主账户长期密钥。
- 仅在可信设备打开生成器；导入成功后删除安卓手机、蓝牙收件箱和 Java 手机中的配置文件。
- 导出备份具有同样风险。若不需要恢复，不要长期保留。

## 文件格式 v2

文件本身是 UTF-8 JSON，最大 32 KB：

```json
{
  "format": "j2me-llm-config",
  "version": 2,
  "encoding": "base64",
  "payload": "eyJhY3RpdmVQcm9maWxlIjoib3BlbmFpIiwiLi4u",
  "crc32": "1234ABCD"
}
```

`payload` 是另一段 UTF-8 JSON 的 Base64，最大 24 KB。`crc32` 是对解码后的 payload 字节计算的标准 CRC-32（多项式 `0xEDB88320`），以 8 位大写十六进制表示。

Payload 结构：

```json
{
  "activeProfile": "openai",
  "profiles": [
    {
      "id": "openai",
      "preset": "openai",
      "name": "OpenAI",
      "endpoint": "https://api.openai.com/v1/chat/completions",
      "modelsEndpoint": "https://api.openai.com/v1/models",
      "apiKey": "sk-...",
      "model": "",
      "systemPrompt": "",
      "stream": true,
      "historyMessages": 8,
      "thinkingMode": 0,
      "thinkingProtocol": 1,
      "reasoningEffort": "low",
      "multimodal": false,
      "endpointOverridden": false
    }
  ]
}
```

取值约定：

- `thinkingMode`: `0` 自动、`1` 开启、`2` 关闭。
- `thinkingProtocol`: `0` 不发送参数、`1` OpenAI `reasoning_effort`、`2` `thinking.enabled/disabled`、`3` Kimi 常开思考。该字段只允许自定义档案编辑；OpenAI、DeepSeek、Kimi 官方档案按模型预设决定，导入时忽略包内覆盖值。
- `multimodal` 默认 `false`；导入配置不会加载图片模块。
- `historyMessages` 范围为 2–24。
- 最多 8 个档案；网页当前编辑 OpenAI、DeepSeek、Kimi、自定义四类各一个档案。
- 档案/预设标识最多 32 字符，档案名称 64 字符。
- 聊天端点与模型端点各 512 字符，API Key 2048 字符，模型名 128 字符。
- 系统提示词 4096 字符，思考强度 32 字符。

网页输入框使用相同的 `maxlength`，生成与载入时还会再次逐字段检查。这里的“字符”与 Java ME `String.length()` 一致，按 UTF-16 代码单元计数；超限配置整包拒绝，不会静默截断。

Java 端应先检查文件大小，再解析外层 JSON、验证格式与版本、Base64 解码、检查 payload 大小和 CRC，最后才解析档案。`ProvisioningCodec` 已按该顺序实现，`ProvisioningFileService` 负责 JSR-75 文件读写。

## 恢复检查

网页的“载入备份检查”会完成版本、大小、Base64、CRC、JSON、档案数量和字段上限检查，再把可识别的四类档案放回表单。它适合在发送前验证刚生成的文件，但不会代替手机端的导入校验。

手机端允许部分配置包：包中出现的档案替换相应槽位，省略档案的 Key、端点、模型、开关和缓存保持不变；未填写 `activeProfile` 时保留当前活动档案。手机导出的备份默认名为 `J2ME-LLM-backup-<毫秒时间戳>.j2cfg`。导出先写同目录临时文件并回读校验，再改名；最终目标已存在时直接拒绝，绝不会截断旧备份。



