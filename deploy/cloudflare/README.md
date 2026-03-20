# Cloudflare Warm Worker 使用教程

本文档说明如何部署和使用 [warm-worker.js](./warm-worker.js)。

该 Worker 的作用是：

- 接收后端或人工发起的预热请求
- 在 Cloudflare 网络内部向目标固件地址发起请求
- 返回当前预热请求的执行结果

适用场景：

- 在字典中选择 `WORKER` 预热策略后，创建、更新和手动预热都会经由 Worker 执行
- 在发布后手工补充 Cloudflare 网络内部预热
- 验证某个固件下载地址是否已经可以被 Cloudflare 边缘节点缓存

## 1. 前置条件

你需要提前准备：

- 一个 Cloudflare 账号
- 已安装 Node.js 18+
- 已安装或可使用 `npx wrangler`
- 一个已接入 Cloudflare CDN 的固件访问域名
- 可被公网访问的固件下载地址，例如：
  `https://fota-cdn.example.com/firmware/v1.0.0/device-a.bin`

建议同时确认以下 CDN 配置已经完成：

- 固件域名已经接入 Cloudflare
- 对固件路径设置了缓存规则，例如 `/firmware/*`
- 已开启合适的 Cache Rules
- 如有需要，已开启 Tiered Cache

## 2. Worker 文件位置

Worker 源码位于：

- [warm-worker.js](./warm-worker.js)

当前实现的请求约束如下：

- 只允许 `POST`
- 请求头必须带 `X-Warm-Token`
- 密钥必须等于环境变量 `WARM_SECRET`
- 请求体至少要包含 `firmware_url`
- 可选传 `ttl`

当前实现的返回格式如下：

```json
{
  "warmed": true,
  "url": "https://fota-cdn.example.com/firmware/v1.0.0/device-a.bin",
  "ttl": 2592000,
  "result": {
    "status": 200,
    "cache": "MISS"
  }
}
```

## 3. 安装 Wrangler

如果本机还没有 Wrangler，可以直接用：

```bash
npm install -g wrangler
```

或者使用：

```bash
npx wrangler --version
```

如果能正常输出版本号，就可以继续。

## 4. 登录 Cloudflare

执行：

```bash
wrangler login
```

浏览器会打开 Cloudflare 授权页面。授权完成后，终端会显示登录成功。

## 5. 创建 `wrangler.toml`

在 `deploy/cloudflare/` 目录下创建一个 `wrangler.toml`：

```toml
name = "fota-warmer"
main = "warm-worker.js"
compatibility_date = "2026-03-20"

[observability]
enabled = true
```

字段说明：

- `name`: Worker 名称，部署后会形成对应的 Worker 服务
- `main`: Worker 入口文件
- `compatibility_date`: Cloudflare Worker 运行时兼容日期

如果你要部署到不同环境，也可以自己加 `env.dev`、`env.prod` 等配置。

## 6. 配置密钥

当前 Worker 使用 `env.WARM_SECRET` 做鉴权，因此必须先设置密钥：

```bash
cd deploy/cloudflare
npx wrangler secret put WARM_SECRET
```

终端会提示你输入密钥。建议：

- 使用长度足够的随机字符串
- 不要把密钥写进 Git
- 后端字典配置中的 `warmSecret` 必须和这里保持一致

## 7. 部署 Worker

进入目录后执行：

```bash
cd deploy/cloudflare
npx wrangler deploy
```

部署成功后，会看到类似输出：

```text
Published fota-warmer
https://fota-warmer.<your-subdomain>.workers.dev
```

这个地址就是后端字典里要配置的 `workerUrl`。

## 8. 手工调用 Worker

### 8.1 最小请求

```bash
curl -X POST "https://fota-warmer.<your-subdomain>.workers.dev" \
  -H "Content-Type: application/json" \
  -H "X-Warm-Token: YOUR_WARM_SECRET" \
  -d '{
    "firmware_url": "https://fota-cdn.example.com/firmware/v1.0.0/device-a.bin"
  }'
```

### 8.2 指定 TTL

```bash
curl -X POST "https://fota-warmer.<your-subdomain>.workers.dev" \
  -H "Content-Type: application/json" \
  -H "X-Warm-Token: YOUR_WARM_SECRET" \
  -d '{
    "firmware_url": "https://fota-cdn.example.com/firmware/v1.0.0/device-a.bin",
    "ttl": 604800
  }'
```

这里的 `ttl` 单位是秒。

### 8.3 返回结果示例

```json
{
  "warmed": true,
  "url": "https://fota-cdn.example.com/firmware/v1.0.0/device-a.bin",
  "ttl": 604800,
  "result": {
    "status": 200,
    "cache": "MISS"
  }
}
```

字段说明：

- `status`: Worker 发起请求时拿到的 HTTP 状态码
- `cache`: 响应头中的 `CF-Cache-Status`

常见 `CF-Cache-Status`：

- `MISS`: 首次命中，说明当前请求触发了缓存写入
- `HIT`: 该节点已存在缓存
- `BYPASS`: 当前请求未被缓存规则接管
- `EXPIRED`: 命中过期缓存并触发回源

## 9. 在本项目中的配置方式

当前项目后端会从字典类型 `cdn_warm.worker.config` 读取配置。

创建版本、更新版本、以及界面手动触发预热，都会统一读取这份配置里的 `strategy` 字段：

- `SERVER`: 使用服务端直接发起预热请求
- `WORKER`: 使用 Cloudflare Worker 发起预热请求

建议在字典中创建一个配置项：

- `dictTypeCode`: `cdn_warm.worker.config`
- `value`: `default`

`extra` 示例：

```json
{
  "strategy": "WORKER",
  "workerUrl": "https://fota-warmer.<your-subdomain>.workers.dev",
  "warmSecret": "YOUR_WARM_SECRET",
  "defaultTtlSeconds": 2592000
}
```

字段说明：

- `strategy`: 预热策略，可选 `SERVER` 或 `WORKER`
- `workerUrl`: Worker 部署地址
- `warmSecret`: 请求头 `X-Warm-Token` 使用的密钥
- `defaultTtlSeconds`: 后端默认传给 Worker 的缓存 TTL 秒数

如果你选择 `SERVER`，后端仍然只会读取这一条配置，但不会使用 `workerUrl` 和 `warmSecret`。

## 10. TTL 规则

当前 [warm-worker.js](/workspace/code/wewins/worktrees/feature-cloudflare-cdn-warm-up/deploy/cloudflare/warm-worker.js) 的 TTL 规则如下：

- 如果请求体里传了合法正整数 `ttl`，优先使用请求值
- 如果没有传 `ttl`，或者传入值非法，则回退到脚本默认值 `86400 * 30`

也就是说，当前优先级是：

1. 请求体里的 `ttl`
2. Worker 内部默认值 `2592000`

如果你的后端字典里配置了 `defaultTtlSeconds`，后端把它传给 Worker 后，就会生效。

## 11. 后端调用时的请求格式

项目后端当前会按如下结构调用 Worker：

```json
{
  "firmware_url": "https://fota-cdn.example.com/firmware/v1.0.0/device-a.bin",
  "ttl": 2592000
}
```

注意：

- 当前 Worker 会使用 `firmware_url`
- 当前 Worker 会优先使用 `ttl`
- 如果没有 `ttl`，则使用脚本默认值 30 天

所以现在只要 `workerUrl`、`warmSecret`、`defaultTtlSeconds` 配对正确，就能正常工作。

## 12. 常见问题排查

### 12.1 返回 `401 Unauthorized`

原因通常是：

- 没有传 `X-Warm-Token`
- 传入的值和 `WARM_SECRET` 不一致

排查方法：

- 重新执行 `wrangler secret put WARM_SECRET`
- 检查字典中的 `warmSecret`
- 检查请求头拼写是否正确

### 12.2 返回 `400 Missing firmware_url`

原因：

- 请求体没有 `firmware_url`
- JSON 结构错误

排查方法：

- 确保请求体是合法 JSON
- 确保字段名是 `firmware_url`，不是 `firmwareUrl`

### 12.3 返回 `405 Method Not Allowed`

原因：

- 使用了 `GET`

解决：

- 改成 `POST`

### 12.4 返回成功但 `cache=BYPASS`

原因通常是：

- 固件域名没有正确接入 Cloudflare
- Cache Rules 没覆盖目标路径
- 源站响应头让 Cloudflare 绕过缓存

排查方法：

- 检查固件 URL 是否真的走了 Cloudflare
- 检查 Cloudflare 缓存规则
- 检查响应头是否存在不合适的 `Cache-Control`

### 12.5 返回 `status=0` 或 `cache=ERROR`

原因通常是：

- Worker 内部请求失败
- 固件地址不可访问
- 域名解析或源站异常

排查方法：

- 先在浏览器或 `curl` 中验证 `firmware_url` 可访问
- 去 Cloudflare Dashboard 查看 Worker 日志

## 13. 查看 Worker 日志

可以在本地实时观察：

```bash
cd deploy/cloudflare
npx wrangler tail
```

然后重新发起一次预热请求，查看日志输出。

## 14. 建议的上线顺序

建议按以下顺序执行：

1. 部署 Worker
2. 设置 `WARM_SECRET`
3. 手工 `curl` 验证 Worker 能成功预热，并确认 `ttl` 生效
4. 在系统字典中配置 `cdn_warm.worker.config`
5. 从管理后台点击“预热”按钮验证联调
6. 检查返回结果和 Cloudflare 缓存状态

## 15. 后续可选增强

如果后面要继续增强这个 Worker，优先建议做这些：

1. 对 `ttl` 增加最大值和最小值约束
2. 对 `firmware_url` 做域名白名单限制，避免被滥用
3. 增加请求体日志字段，例如 `versionId`、`operator`
4. 增加更细的响应信息，例如耗时、失败原因
