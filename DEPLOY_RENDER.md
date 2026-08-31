# Render 免费平台部署指南

本指南将帮助你在 Render（免费PaaS平台）上部署西秦模型管理平台。

## 架构概览

| 组件 | 部署方式 | 费用 |
|------|----------|------|
| 前端 (Vue 3) | Render Static Site | 免费 |
| 后端 (Spring Boot) | Render Web Service (Docker) | 免费（15分钟无请求休眠） |
| PostgreSQL | Render Managed Database | 免费90天 |
| Redis | Upstash（外部服务） | 免费（10K命令/天） |
| RabbitMQ | CloudAMQP（外部服务） | 免费（1M消息/月） |
| 对象存储 | Backblaze B2（外部服务） | 免费（10GB） |
| 模型转换Worker | Render Background Worker | 需付费（可先不部署） |

## 第一步：准备外部免费服务

### 1.1 注册 Upstash Redis（免费）

1. 访问 https://upstash.com/，用 GitHub 账号登录
2. 点击 **Create Database**
3. 名称填 `xiqin-redis`，地区选离你最近的（如 `us-east-1`）
4. 类型选 **Regional**，点击 **Create**
5. 创建后在 **Details** 页面找到：
   - **Endpoint**（主机名，不含端口，如 `redis-12345.upstash.io`）
   - **Port**（如 `6379`）
   - **Password**（点击 **Show** 查看）
6. 记录这三个值，稍后填入 Render 环境变量

### 1.2 注册 CloudAMQP RabbitMQ（免费）

1. 访问 https://www.cloudamqp.com/，点击 **Get Started**
2. 选择 **Free Plan**（Little Lemur，免费）
3. 用 GitHub 或邮箱注册
4. 创建实例：
   - 名称填 `xiqin-mq`
   - 地区选离你最近的
   - 计划选 **Little Lemur (Free)**
5. 创建后在实例详情页找到：
   - **Host**（如 `shrimp.rmq.cloudamqp.com`）
   - **Port**（AMQP端口，如 `5672`）
   - **Username** 和 **Password**（在 **AMQP Details** 中）
6. 记录这四个值

### 1.3 注册 Backblaze B2 对象存储（免费10GB）

1. 访问 https://www.backblaze.com/，点击 **Sign Up**
2. 注册账号（免费10GB存储）
3. 登录后进入 **Buckets**，点击 **Create a Bucket**
4. 名称填 `xiqin-models`，选择 **Private**，点击 **Create**
5. 进入 **App Keys**，点击 **Add a New Application Key**
6. 名称填 `xiqin-app-key`，选择刚才创建的 bucket，权限选 **Read and Write**，点击 **Create**
7. 记录：
   - **keyID**（即 Access Key）
   - **applicationKey**（即 Secret Key）
8. 在 Bucket 详情页找到 **Endpoint**（如 `https://s3.us-east-005.backblazeb2.com`）
9. 记录这三个值

## 第二步：在 Render 上部署

### 2.1 注册 Render

1. 访问 https://render.com/，点击 **Get Started**
2. 用 GitHub 账号登录并授权

### 2.2 使用 Blueprint 一键部署

1. 在 Render Dashboard 点击 **New +** → **Blueprint**
2. 连接你的 GitHub 仓库（`xiqin-model-platform`）
3. Render 会自动读取 `render.yaml`，显示将要创建的服务
4. 在环境变量页面，把所有 `YOUR_` 开头的占位符替换为第一步记录的值：
   - `SPRING_REDIS_HOST` → Upstash 的 Endpoint
   - `SPRING_REDIS_PORT` → Upstash 的 Port
   - `SPRING_REDIS_PASSWORD` → Upstash 的 Password
   - `SPRING_RABBITMQ_HOST` → CloudAMQP 的 Host
   - `SPRING_RABBITMQ_PORT` → CloudAMQP 的 Port
   - `SPRING_RABBITMQ_USERNAME` → CloudAMQP 的 Username
   - `SPRING_RABBITMQ_PASSWORD` → CloudAMQP 的 Password
   - `MINIO_ENDPOINT` → Backblaze B2 的 Endpoint
   - `MINIO_ACCESS_KEY` → Backblaze B2 的 keyID
   - `MINIO_SECRET_KEY` → Backblaze B2 的 applicationKey
5. 点击 **Apply** 开始部署
6. 等待 5-10 分钟（后端 Docker 构建需要下载 Maven 依赖）

### 2.3 验证部署

1. 部署完成后，在 Render Dashboard 点击 `xiqin-frontend` 服务
2. 点击服务 URL（如 `https://xiqin-frontend.onrender.com`）
3. 使用默认账号登录：
   - 用户名：`admin`
   - 密码：`admin@xiqin2024`
4. 登录后请立即修改密码

## 第三步：（可选）启用模型转换 Worker

模型上传后需要 Worker 进行 FBX→GLB 转换。Render 免费版不支持 Background Worker，如需启用：

1. 在 Render 升级到付费计划（最低 $7/月）
2. 编辑 `render.yaml`，取消 `xiqin-worker` 部分的注释
3. 填写 `RABBITMQ_URL`（CloudAMQP 的 AMQP URL，格式为 `amqp://user:pass@host:port/vhost`）
4. 提交代码，Render 会自动部署 Worker

不启用 Worker 时，模型上传后会停留在"处理中"状态，其余功能（浏览、项目管理、权限等）正常使用。

## 常见问题

### Q: 后端服务启动失败怎么办？
A: 在 Render 后端服务的 **Logs** 中查看错误信息。常见原因：
- Redis/RabbitMQ 连接信息错误 → 检查环境变量
- 数据库连接失败 → 确认 PostgreSQL 已创建且 IP 允许列表包含 `0.0.0.0/0`
- 健康检查失败 → 确认 `healthCheckPath` 为 `/api/actuator/health`

### Q: 前端页面空白或API请求失败？
A: 
- 检查浏览器控制台，确认 API 请求地址是否正确
- 确认后端服务已启动且未休眠（免费版15分钟无请求会休眠，首次访问需等待30秒唤醒）
- 确认 `CORS_ALLOWED_ORIGINS` 包含前端地址

### Q: 免费版有什么限制？
A:
- 后端Web服务：15分钟无请求自动休眠，首次访问需等待30秒
- PostgreSQL：免费90天，到期后需升级或数据会被删除
- 构建时间：每月免费500分钟
- 带宽：每月免费100GB

### Q: 如何更新代码？
A: 直接 push 到 GitHub 仓库的 main 分支，Render 会自动检测并重新部署（`autoDeploy: true`）。

### Q: 如何绑定自定义域名？
A: 在 Render 服务设置中添加自定义域名，然后在域名DNS服务商添加 CNAME 记录指向 Render 提供的地址。
