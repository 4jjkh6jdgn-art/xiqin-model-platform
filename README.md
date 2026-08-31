# 西秦模型管理平台

3D模型资产全生命周期管理平台，支持FBX/OBJ/GLB浏览器直接预览、文件夹一键上传、自动模型转换、全流程权限管控。

## 功能特性

- **3D实时预览** — FBX/OBJ/GLB/STL等格式浏览器直接预览，无需下载
- **文件夹一键上传** — 自动识别主模型与贴图文件，自动关联材质
- **模型自动转换** — 后台Worker自动将FBX等格式转换为GLB，支持超大模型
- **项目管理** — 模型可关联多个项目，支持项目分类、版本管理、协作成员
- **分类浏览** — 模型分类和项目分类，支持多级分类和搜索
- **全流程权限管控** — 注册审批 + 角色权限 + 操作记录留痕
- **批量操作** — 批量删除、批量上传、批量分类
- **自定义配色** — 支持系统主题色自定义，内置多套配色方案

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Three.js |
| 后端 | Java Spring Boot + PostgreSQL + Redis + RabbitMQ |
| 对象存储 | MinIO |
| 模型转换 | Node.js + Three.js (FBXLoader/GLTFExporter) + node-canvas |
| 部署 | Docker + Docker Compose |

## 快速部署

### 环境要求

- Docker 20.10+
- Docker Compose v2+
- 至少 4GB 内存（建议8GB以上，大模型转换需要更多内存）
- 至少 10GB 磁盘空间

### 部署步骤

```bash
# 1. 克隆项目
git clone <你的仓库地址>
cd xiqin-model-platform

# 2. 复制环境变量配置并修改密码
cp .env.example .env
# 编辑 .env，修改以下密码：
#   POSTGRES_PASSWORD
#   REDIS_PASSWORD
#   RABBITMQ_PASSWORD
#   MINIO_ROOT_PASSWORD
#   JWT_SECRET（必须修改，至少32位随机字符串）

# 3. 构建并启动所有服务
docker compose -f docker-compose.prod.yml --env-file .env up -d --build

# 4. 等待启动完成（约1-2分钟）
# 后端会自动执行数据库迁移（Flyway V1-V15）并创建初始管理员

# 5. 访问系统
# 浏览器打开 http://localhost:8088
# 默认管理员账号：admin / admin@xiqin2024
```

### 局域网访问

如需局域网内其他设备访问，修改 `.env` 中的：

```env
APP_BIND_IP=0.0.0.0
MINIO_PUBLIC_ENDPOINT=http://<你的本机IP>:8088/minio
```

然后重启前端和后端：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --force-recreate frontend backend
```

其他设备通过 `http://<你的本机IP>:8088` 访问。

## 项目结构

```
xiqin-model-platform/
├── backend/                 # Java Spring Boot 后端
│   ├── src/main/java/      # 源代码
│   ├── src/main/resources/ # 配置文件 + Flyway迁移脚本
│   └── pom.xml
├── frontend/                # Vue 3 前端
│   ├── src/                 # 源代码
│   ├── public/              # 静态资源
│   ├── vite.config.ts       # Vite配置
│   ├── nginx.conf           # 生产环境Nginx配置
│   └── package.json
├── worker/                  # Node.js 模型转换Worker
│   ├── src/
│   │   ├── converter.js     # FBX→GLB转换器（贴图压缩、几何体拆分）
│   │   ├── processor.js     # 处理流水线（下载→转换→上传→通知）
│   │   └── index.js         # 入口
│   └── package.json
├── scripts/                 # 维护脚本
├── nginx/                   # Nginx配置
├── docker-compose.yml       # 开发环境编排
├── docker-compose.prod.yml  # 生产环境编排
├── .env.example             # 环境变量示例
└── README.md
```

## 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| frontend | 8088 | 前端Nginx（可通过APP_PORT修改） |
| backend | 8080（内部） | Spring Boot后端API |
| postgres | 5432（内部） | PostgreSQL数据库 |
| redis | 6379（内部） | Redis缓存 |
| rabbitmq | 5672（内部） | RabbitMQ消息队列 |
| minio | 9000（内部） | MinIO对象存储 |
| worker | - | 模型转换Worker（无外部端口） |

所有内部服务不对外暴露，仅通过frontend的8088端口访问。

## 模型转换说明

Worker自动处理模型转换：
1. 从MinIO下载FBX和贴图文件
2. 使用Three.js FBXLoader解析
3. 自动匹配并嵌入外部贴图
4. 贴图自动压缩（超过1024px自动缩放）
5. 多材质Mesh自动拆分（避免GLTF导出重叠）
6. 使用GLTFExporter导出GLB
7. 上传回MinIO并通知后端

大模型转换需要较长时间，可在模型详情页查看处理状态。

## 数据备份

### 数据库备份

```bash
docker exec xiqin-postgres-1 pg_dump -U xiqin -d xiqin -F c > backup_$(date +%Y%m%d).dump
```

### MinIO数据备份

```bash
docker exec xiqin-minio-1 mc mirror local/models/ /tmp/models_backup/
docker cp xiqin-minio-1:/tmp/models_backup/ ./minio_backup/
```

## 常见问题

**Q: 初始管理员密码是什么？**
A: 默认 `admin / admin@xiqin2024`，可在 `.env` 中通过 `INITIAL_ADMIN_PASSWORD` 修改。首次启动后修改密码不会影响已创建的账号。

**Q: 模型转换失败怎么办？**
A: 检查Worker日志 `docker logs xiqin-worker-1`，常见原因是内存不足。大模型建议至少8GB内存。

**Q: 如何修改系统配色？**
A: 登录后进入「个人设置」→「系统配色」，可选择内置配色或自定义。

**Q: 上传的模型文件存在哪里？**
A: 所有文件存储在MinIO的 `models` bucket中，按 `模型ID/版本/类型/` 目录结构组织。

## 许可证

本项目仅供内部使用。
