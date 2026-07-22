# J2ME LLM v0.2 HTTPS 兼容网关

这个零依赖的 Node.js 18+ 网关用于解决老式 J2ME 手机无法协商现代 HTTPS/TLS 的问题。手机只连接受信任局域网中的 HTTP 网关，网关再通过现代 HTTPS 访问 LLM 服务商。真实服务商密钥只保存在网关电脑上，不需要写入手机。

v0.2 同时代理聊天和模型目录，因此 MIDlet 的“获取模型”功能在索爱 W995 等旧设备上也可以通过网关工作。

## 暴露的接口

| 方法与路径 | 是否需要设备令牌 | 用途 |
| --- | --- | --- |
| GET /health | 否 | 局域网连通性检查，不包含配置或密钥 |
| POST /v1/chat/completions | 是 | 转发 OpenAI 兼容聊天请求和流式响应 |
| GET /v1/models | 是 | 转发 OpenAI 兼容模型列表 |

聊天和模型接口都要求请求头 Authorization: Bearer DEVICE_TOKEN。网关会把这个设备令牌替换为真正的 UPSTREAM_API_KEY，再请求上游。上游状态码、Content-Type 和可用的 x-request-id 会保留，响应正文按流转发，不会先完整缓存在内存中。

## 运行要求

- Node.js 18 或更高版本（需要内置 fetch）。
- 一台能访问服务商 HTTPS API 的可信电脑。
- 手机和网关电脑位于同一受信任局域网。
- 主机防火墙只允许需要的局域网设备访问网关端口。

本程序不会自动读取 .env 文件；gateway/.env.example 只是变量清单。可以由系统服务、容器或启动脚本注入变量。

## 环境变量

| 变量 | 必填 | 默认值 / 说明 |
| --- | --- | --- |
| HOST | 否 | 默认 127.0.0.1；真机访问时通常设为 0.0.0.0 |
| PORT | 否 | 默认 8787 |
| UPSTREAM_URL | 否 | 默认 https://api.openai.com/v1/chat/completions |
| UPSTREAM_MODELS_URL | 否 | 模型目录地址；留空时从 UPSTREAM_URL 自动推导 |
| UPSTREAM_API_KEY | 是 | 服务商真实密钥，只保存在网关电脑 |
| UPSTREAM_MODEL | 否 | 设置后强制覆盖手机请求中的模型；留空则使用手机所选模型 |
| DEVICE_TOKEN | 是 | 手机使用的网关令牌，至少 12 个字符，建议随机生成 32 字符以上 |
| LOG_ERRORS | 否 | 设为 1 时输出精简错误；已知密钥和设备令牌会被脱敏 |

自动推导只接受以 /chat/completions 或 /chat/completions/ 结尾的聊天路径，并将最后一段替换成 /models。域名、前缀路径和查询参数会保留。若服务商采用其他路由，必须显式填写 UPSTREAM_MODELS_URL；启动阶段会尽早报错，避免悄悄请求错误地址。

所有上游地址必须使用 HTTPS。生产环境没有允许 HTTP 上游的环境变量，程序化的 allowInsecureUpstream 只供本地自测创建临时 HTTP 服务器使用。上游重定向也会被拒绝，避免携带密钥降级到 HTTP。

## PowerShell 启动示例

先复制 gateway/.env.example 中的值，按实际服务商修改：

~~~powershell
$env:UPSTREAM_URL='https://api.openai.com/v1/chat/completions'
$env:UPSTREAM_MODELS_URL='https://api.openai.com/v1/models'
$env:UPSTREAM_API_KEY='your-real-provider-key'
$env:UPSTREAM_MODEL=''
$env:DEVICE_TOKEN=[guid]::NewGuid().ToString('N')
$env:HOST='0.0.0.0'
$env:PORT='8787'
node .\gateway\server.js
~~~

常见上游组合：

| 服务商 | UPSTREAM_URL | UPSTREAM_MODELS_URL |
| --- | --- | --- |
| OpenAI | https://api.openai.com/v1/chat/completions | https://api.openai.com/v1/models |
| DeepSeek | https://api.deepseek.com/chat/completions | https://api.deepseek.com/models |
| Kimi 国内站 | https://api.moonshot.cn/v1/chat/completions | https://api.moonshot.cn/v1/models |

这三组都可以把 UPSTREAM_MODELS_URL 留空自动推导；显式配置更容易排查自定义反向代理的路径问题。

## MIDlet 配置

假设网关电脑的局域网地址是 192.168.1.20，端口是 8787：

- 聊天端点：http://192.168.1.20:8787/v1/chat/completions
- 模型端点：http://192.168.1.20:8787/v1/models
- API Key：填写 DEVICE_TOKEN，而不是真实服务商密钥
- 模型：UPSTREAM_MODEL 留空时由手机档案决定；设置后以网关值为准

v0.2 的预设档案若改用网关，应同时把聊天端点和模型端点指向上述两个地址。先在手机浏览器访问 /health 可以检查 IP、端口和防火墙；健康检查不代表服务商密钥一定有效。

## 安全边界

- 手机到网关的普通 HTTP 会暴露设备令牌、提示词、回复和图片给能够监听该局域网的人，只能用于可信网络。
- 不要把 HOST=0.0.0.0 的监听端口直接暴露到公网。仅模拟器使用时保留默认 HOST=127.0.0.1。
- DEVICE_TOKEN 不是上游密钥，但仍应随机生成、定期更换，并通过防火墙限制来源。
- 网关不会记录请求正文或响应正文，也不会把 UPSTREAM_API_KEY 返回给手机。开启 LOG_ERRORS 后只打印脱敏后的错误消息。
- 如果需要跨互联网使用，应在网关前部署手机能够验证的 TLS，或使用与该旧手机兼容的私有网络方案。
- UPSTREAM_API_KEY 泄漏后应立即在服务商控制台撤销；DEVICE_TOKEN 泄漏后应更换并重启网关。

## 验证

在项目根目录运行：

~~~powershell
node --check .\gateway\server.js
node .\gateway\self-test.js
~~~

自测会使用只监听 127.0.0.1 的临时 HTTP 上游，不访问互联网，也不使用真实密钥。它覆盖：

- 未授权聊天和模型请求返回 401；
- 授权 GET /v1/models 的路径推导、上游 Authorization、状态与 Content-Type 转发；
- POST /v1/chat/completions 的模型覆盖和 SSE 流式响应；
- 两类请求的 J2ME-LLM-Gateway/0.2.0 User-Agent；
- 生产配置拒绝 HTTP 上游，并拒绝无法推导的模型路径。

## 常见问题

手机无法连接：确认电脑 IP 没有变化、HOST 已设为 0.0.0.0、两台设备在同一网络，并检查 Windows 防火墙的入站规则。公共 Wi-Fi 的客户端隔离会阻止手机访问电脑。

聊天可用但模型列表失败：显式填写 UPSTREAM_MODELS_URL，重启网关后再获取。某些兼容服务只实现聊天而没有 /models，此时继续手工填写模型名即可。

返回 401 Invalid device token：手机里填写的必须是 DEVICE_TOKEN。真实 UPSTREAM_API_KEY 只应存在于网关环境变量中。

返回 502 Upstream request failed：检查上游 URL、DNS、服务商网络可达性和真实密钥。可临时设置 LOG_ERRORS=1 查看脱敏错误，排查后再关闭。

