



# J2ME LLM v0.2 架构与兼容性

本文面向维护者，说明 v0.2 如何在 CLDC 1.1 / MIDP 2.0 的限制下组织多档案、网络流、RMS、离线配置和可选图片。目标不是把桌面端架构缩小后照搬，而是把高峰内存、TLS 差异、断电写入和厂商协议差异当作一等约束。

## 1. 运行边界

- Java 源码按 Java 1.3 语法编写，目标 class 版本为 Java 1.1；不使用泛型、枚举、注解、lambda、`java.nio` 或 try-with-resources。
- 核心依赖 CLDC 1.1 与 MIDP 2.0；文件导入、导出和本地图片选择依赖可选 JSR-75。
- UI 使用 LCDUI `Canvas`/`Form`/`List`，网络使用 `HttpConnection`，持久化使用 RMS `RecordStore`。
- 聊天协议为 OpenAI Chat Completions 兼容协议；不是完整 Responses API 客户端。
- 后台线程负责 HTTP、配置包导入/导出和返回图片下载；文件选择器的目录枚举与本地图片读取由 LCDUI 回调同步触发并限制为 256 项。LCDUI 状态切换和后台结束回调回到 `Display.callSerially` 执行。

## 2. 模块分层

| 层 | 主要类 | 职责 |
| --- | --- | --- |
| 应用控制 | `LlmMidlet` | 生命周期、当前档案与会话、界面导航、请求协调、保存与内存回收 |
| UI | `ChatCanvas`、`SettingsForm`、档案/模型/配置文件界面、`ImagePicker`、`ImageDimensions` | 气泡绘制、命令、档案编辑、按需模型选择、可选文件访问 |
| 模型 | `ProviderProfile`、`ProviderPresets`、`ProfileState`、`ChatMessage`、`MessageMedia` | 四档案状态、思考策略元数据、消息与延迟分配的媒体数据 |
| 聊天网络 | `OpenAiChatClient`、`ChatRequestWriter`、`JsonStreamWriter`、`ByteLineReader`、`ThinkingFilter` | 两遍计数/流式写请求、SSE 或 JSON 响应、正文/思考/兼容图片分离 |
| 模型目录 | `ModelCatalogClient`、`ModelCatalogParser` | 用户显式触发 GET `/models`，增量提取 `data[].id` |
| RMS | `ProfileStore`、`ProfileCodec`、`ProfileConversationStore`、`ConversationRecordValidator`、`LegacyConfigCodec` | 主备记录、CRC、档案隔离、v0.1 只读迁移 |
| 离线配置 | `ProvisioningCodec`、`ProvisioningFileService`、`ProvisioningPackage` | `.j2cfg` 编解码、边界验证、JSR-75 文件读写 |
| TLS 兼容 | `gateway/server.js` | 可信局域网 HTTP 到现代上游 HTTPS 的有限反向代理 |

旧的 `ProviderConfig`、`ConfigStore` 和 `ConversationStore` 保留为 v0.1 格式定义/迁移来源；v0.2 正常路径使用 profile 版本。

## 3. 核心数据流

### 3.1 启动与档案加载

```text
MIDlet start
  -> ProfileStore.load()
      -> v0.2 主记录有效：加载
      -> 主记录坏、备份有效：加载备份并修复主记录
      -> v0.2 库不存在：读取 v0.1 J2MELLM_CFG，迁入 custom
  -> 选择 activeProfileId
  -> ProfileConversationStore(active).load()
      -> 必要时把 v0.1 J2MELLM_CHAT 迁入 custom
  -> ChatCanvas 绑定当前档案与其消息 Vector
```

档案切换前保存当前会话，切换后换用对应 `J2CHAT_<id>`。聊天 Vector 不跨档案共享。

### 3.2 一次流式聊天请求

```text
用户消息
  -> 当前档案能力检查
  -> 会话预算回收、构造请求历史快照
  -> ChatRequestWriter 第 1 遍：只计 UTF-8/JSON/Base64 字节数
  -> HttpConnection 设置 Content-Length
  -> ChatRequestWriter 第 2 遍：512 B 缓冲直接写网络输出流
  -> 释放请求图片原始 byte[]
  -> ByteLineReader 逐行读取 SSE
  -> JSON 解码 delta
  -> ThinkingFilter 分流正文与思考
  -> ChatMessage revision 变化
  -> ChatCanvas 每 100 ms 至多安排一次流式重绘
  -> 完成后预算回收并写当前档案历史主备记录
```

两遍写出换取低峰值内存和老 HTTP 栈兼容性：第一遍不创建整份请求字符串，只计算实际 UTF-8 字节数；第二遍直接写出。Base64 也逐三字节编码到输出流，不产生一份 4/3 大小的中间字符串。图片源字节本身仍需要暂存到请求写完，因此图片有更严格的体积和可用内存门槛。

### 3.3 模型目录

```text
用户选择“获取模型”
  -> GET 当前档案 modelsEndpoint + Bearer Key
  -> 每次读取 512 B
  -> ModelCatalogParser 增量校验 UTF-8 与 JSON 状态
  -> 只保留根 data 数组内对象的 id
  -> 去重，最多 64 项
  -> 写回当前档案缓存和 modelsCachedAt
```

解析器不构造完整 JSON 对象树，不保存 `created`、`owned_by` 等无关字段；响应最大 128 KiB、嵌套最大 20 层、模型 ID 最大 256 字符。缓存刷新完全由用户触发，不在启动路径上联网。

## 4. 档案与预设模型

`ProfileState` 固定规范化为 `openai`、`deepseek`、`kimi`、`custom` 四个稳定 ID。稳定 ID 同时用于 UI 定位、RMS 历史名和 `.j2cfg` 映射，显示名称可以改变但 ID 不变。

`ProviderProfile` 主要字段分为：

- 身份：`id`、`presetId`、`name`；
- 网络：`endpoint`、`modelsEndpoint`、`apiKey`、`endpointOverride`；
- 对话：`model`、`systemPrompt`、`stream`、`historyMessages`；
- 思考：`thinkingMode`、`thinkingProtocol`、`reasoningEffort`、`reasoningExpanded`；
- 可选媒体：`multimodal`；
- 模型缓存：最多 64 个 ID 与 `modelsCachedAt`。

内置端点在未开启覆盖时视为锁定，避免误改；自定义档案默认允许编辑。`deriveModelsEndpoint` 只做保守的路径替换/追加，不探测服务端。

## 5. 思考协议适配

请求模式与显示折叠分离：

- `thinkingMode`：AUTO / ON / OFF，影响后续请求；
- `reasoningExpanded`：只影响 Canvas 是否展开已收到的思考文本。

`ChatRequestWriter` 按 `thinkingProtocol` 映射根请求字段：

| 协议常量 | AUTO | ON | OFF |
| --- | --- | --- | --- |
| `NONE` | 省略 | 省略 | 省略 |
| `OPENAI_EFFORT` | 省略 | `reasoning_effort=<effort>` | `reasoning_effort=none` |
| `ENABLED_OBJECT` | 省略 | `thinking.type=enabled`，适用时带 effort | `thinking.type=disabled` |
| `KIMI`/常开思考 | 省略 | K3 发送 `reasoning_effort`；K2.7 Code 不发送控制字段 | 归一为开启，不发送虚假的关闭参数 |

Kimi 档案根据模型名识别 K3 与 K2.7 Code 常开思考模型；K2.5/K2.6 使用 `thinking.type` 且不附带 `reasoning_effort`；Moonshot V1 或未来未知型号不猜测参数，ON/OFF 均不发送控制字段。对这类多轮聊天，助手消息的 `reasoning_content` 会与正文一并送回，避免破坏要求保留思考上下文的兼容协议。这样会增加上下文体积，仍受全局消息预算和 `historyMessages` 限制。

允许的 effort 文本为 `minimal`、`low`、`medium`、`high`、`xhigh`、`max`，无效值回退到 `low`。这只是客户端白名单，不代表所有提供商/模型都支持每一档。权威定义见 [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[DeepSeek 思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode) 和 [Kimi K2 Thinking 指南](https://platform.kimi.com/docs/guide/use-kimi-k2-thinking-model)。

响应侧接受 `reasoning_content`、`reasoning`、`analysis`，并用 `ThinkingFilter` 从普通正文中分离 `<think>…</think>`。这是一层兼容策略，不声称返回模型的完整内部推理。

## 6. 多模态延迟加载

`ProviderProfile.multimodal` 默认 `false`。控制层和 UI 在关闭状态不进入图片选择/响应图片加载路径；请求写出器也不读取消息图片字节，响应解析器不扫描图片扩展字段。

`ChatMessage` 始终只含正文、思考和几个标志；图片名称、MIME、字节、来源、状态和 LCDUI 预览集中在一个可空的 `MessageMedia`，只有消息实际出现媒体时才分配。这避免为每条纯文字消息持有多组空字段和 Image 引用。

打开多模态后：

- `ImagePicker` 通过反射/可选 JSR-75 路径加载文件，目录最多显示 256 项，文件最大 96 KiB；
- `ImageDimensions` 只读 PNG/GIF/JPEG/WebP 头，确认解码像素不超过 65,536 后才调用平台图片解码器；未知或超限返回图跳过预览；
- 读已知大小文件前要求可用堆大致不低于 `3 × 文件大小 + 64 KiB`；
- 捕获 `OutOfMemoryError` 并返回可理解的错误，避免让整个 MIDlet 无提示退出；
- 请求图片写为 `image_url` data URL，Base64 流式编码，`detail=low`；
- 请求结束立即释放图片原始 byte[]；
- 兼容返回图片下载上限 256 KiB，且仍受 65,536 解码像素上限约束，预览按屏幕宽度缩放；
- RMS 不保存图片字节或 data URL，只保存受限元数据/远程来源。

Chat Completions 的标准能力以 [OpenAI Create chat completion](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create) 为准；应用识别的图片输出字段属于兼容扩展。

## 7. 内存预算与峰值控制

老 MIDP 手机的堆可能只有数 MiB，而且 `String`、`Image` 和厂商 HTTP 栈还会产生不可见副本。v0.2 采用多层上限，不依赖单一 OOM 捕获：

| 对象/路径 | 上限或策略 |
| --- | --- |
| 单条消息正文 | 24,576 字符 |
| 单条消息思考 | 8,192 字符 |
| 活动会话 | 控制层按 49,152 字符、最多 32 条的双重预算回收最旧消息，保留最近对话 |
| 一次请求历史 | 当前档案 `historyMessages`，规范化为 2–24 |
| 每档案 RMS 历史 | 最近 24 条已完成消息 |
| RMS 单条持久化正文/思考 | 5,000 / 1,500 字符 |
| SSE 单行 | 64 KiB |
| 非流式普通响应 | 256 KiB |
| 非流式多模态响应 | 512 KiB |
| HTTP 错误响应 | 32 KiB（模型目录错误 16 KiB） |
| 模型目录 | 128 KiB、64 ID |
| 本地发送图片 | 96 KiB、65,536 像素，加读前可用堆检查 |
| 远程返回图片 | 256 KiB、65,536 解码像素 |
| `.j2cfg` / payload | 32 KiB / 24 KiB |

“字符预算”不等于精确字节预算：Java `char`、对象头和 VM 实现会改变实际占用。它是确定性回收门槛，不是保证任何设备永不 OOM。真机验收仍需覆盖长中文、长英文、连续 SSE、小堆和图片组合。

## 8. Canvas 布局缓存与重绘节流

逐 token 流式响应最容易把 CPU 耗在重复换行和整屏绘制上。`ChatCanvas` 为每条消息保存 `MessageLayout`：

- 缓存已换行的正文、思考、状态文字及气泡高度；
- key 包含消息对象、`revision`、画布宽度和思考展开状态；
- 消息内容、媒体、pending/error 状态改变时 revision 失效；
- 档案切换、宽度或折叠状态改变时重建相关布局；
- paint 仍测量总高度，但只真正绘制落在可视区域的消息；
- 请求进行中 `contentChanged()` 合并为约每 100 ms 一次 repaint，完成后立即重绘。

缓存以消息数量线性增长，切换会话和清空历史时释放。节流线程只负责安排 repaint，不直接调用 LCDUI 绘图。

## 9. RMS 一致性模型

`ProfileCodec` 与 v0.2 历史格式都使用 magic、格式版本和 CRC-32；保存旧主记录前只校验信封头、档案 ID、计数边界和 CRC，不重复构建整份消息 Vector。保存采用两条记录：旧有效主记录先复制到备份，再写新主记录；读取先主后备，备份可用时修复主记录。

配置库存在但主备都坏时，不再自动套用 v0.1 数据，以免把“存储损坏”误判成“从未迁移”。旧 `J2MELLM_CFG`/`J2MELLM_CHAT` 在成功迁移后保留。完整状态机与测试矩阵见 [覆盖更新与恢复指南](UPDATE_AND_RECOVERY.md)。

CRC 只发现随机损坏，不抵抗恶意修改；RMS 也不加密。卸载 suite 会删除其 RMS，见 [Oracle RecordStore](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/rms/RecordStore.html)。

## 10. `.j2cfg` 边界

外层是 UTF-8 JSON：格式名、版本、Base64 payload 和 payload CRC。payload 是第二个 UTF-8 JSON，承载档案字段。解析顺序刻意先执行便宜的大小与结构检查，再做解码和对象构造：

```text
file <= 32 KiB
 -> outer JSON object
 -> format/version/encoding
 -> encoded payload length
 -> Base64 decode
 -> payload <= 24 KiB
 -> CRC-32 equality
 -> payload JSON
 -> <= 8 profiles + per-field bounds + unique IDs + active ID exists
 -> convert to ProfileState
 -> RMS save
```

`ProvisioningFileService` 是唯一直接依赖 `javax.microedition.io.file` 的配置模块；普通启动不实例化它。默认导出到 `FileSystemRegistry` 返回的第一个根目录，文件名 `J2ME-LLM-backup-<毫秒时间戳>.j2cfg`。写入先落到同目录临时文件并逐字节回读，随后改名；同名最终文件存在时拒绝，不截断旧备份。

## 11. HTTPS 兼容网关边界

附带 Node.js 网关只接收：

- `GET /health`；
- 带 Bearer 设备令牌的 `POST /v1/chat/completions`。

它限制手机请求体为 1 MiB，校验 `messages` 数组，可按环境变量覆盖模型，把响应状态和 SSE 数据流回手机。上游默认必须为 HTTPS；设备令牌至少 12 字符并用定时安全比较验证。

网关**不是通用开放代理**，只接受鉴权后的 `POST /v1/chat/completions`、`GET /v1/models` 和无需鉴权的 `/health`；其他路径返回 404。聊天与模型目录都由网关换成真实上游 Key 后转发。局域网段仍是明文，必须配合可信 Wi-Fi、监听地址和防火墙；详见 [gateway/README.md](../gateway/README.md)。

## 12. 协议兼容性矩阵

| 能力 | 标准/常见形态 | v0.2 行为 |
| --- | --- | --- |
| 聊天请求 | `POST /chat/completions` | 支持文字、SSE、非流式 JSON |
| 模型列表 | `GET /models`, `data[].id` | 显式请求、增量解析和缓存 |
| 思考请求 | `reasoning_effort` 或 `thinking.type` | 按档案协议映射；AUTO 省略 |
| 思考响应 | `reasoning_content`/`reasoning`/`analysis`/`<think>` | 分离到折叠区域 |
| 图片输入 | `content` parts + `image_url` | 多模态开启时发 data URL，detail low |
| 图片输出 | 厂商私有 content part、images、b64_json、URL | 多模态开启时尽力识别；非标准保证 |
| 工具调用 | `tools`/`tool_calls` | v0.2 不实现 |
| Responses API | `/responses` | v0.2 不作为聊天主协议 |

上游“OpenAI 兼容”常只覆盖字段子集。自定义端点应先用 AUTO + 纯文字 + 非敏感短提示验证，再逐项开启流式、思考和图片。

## 13. 威胁模型与非目标

v0.2 防护重点是意外损坏、峰值内存和误暴露，不包含完整安全沙箱：

- RMS/`.j2cfg` 不加密；拿到设备或文件的人可能读取 Key；
- CRC 不提供数字签名；
- HTTP 网关链路不保密；
- 远程服务端能看到提示词和图片；
- 不实现证书 pinning、自定义 CA 或端到端消息加密；
- 不保证厂商对私有思考/图片字段的长期兼容。

推荐把上游真实 Key 留在受控网关，手机只持可撤销的设备令牌，并把 `.j2cfg` 当成密码文件管理。

## 14. 官方参考

- [OpenAI List models](https://developers.openai.com/api/reference/resources/models/methods/list)
- [OpenAI Create chat completion](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
- [DeepSeek API 文档](https://api-docs.deepseek.com/)
- [DeepSeek 思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode)
- [Kimi API 概览](https://platform.kimi.com/docs/api/overview)
- [Kimi 模型目录](https://platform.kimi.com/docs/models)
- [Kimi K2 Thinking 指南](https://platform.kimi.com/docs/guide/use-kimi-k2-thinking-model)
- [Oracle Java ME RMS RecordStore](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/rms/RecordStore.html)

协议链接与预设说明按 2026-07-22 核对。






