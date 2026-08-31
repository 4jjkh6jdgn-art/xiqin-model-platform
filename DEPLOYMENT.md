# 西秦模型管理平台：Linux 服务器部署

本方案面向一台全新的局域网 Linux 服务器，不迁移旧 Docker Toolbox 数据。平台网页和 ONLYOFFICE 文档服务是两个局域网入口；PostgreSQL、Redis、RabbitMQ、MinIO 和后端 API 均不直接映射到宿主机端口。

## 1. 服务器要求

- Linux x86_64（推荐 Ubuntu 22.04/24.04 LTS）
- Docker Engine 24 或更高版本
- Docker Compose v2.20 或更高版本（命令形式为 `docker compose`）
- 最低 4 核、8 GB 内存；模型转换任务较大时建议 16 GB 内存
- 系统盘预留至少 20 GB，模型文件按实际业务量准备独立数据盘
- 防火墙仅向本地局域网开放 SSH 和平台访问端口；数据库及中间件端口无需开放

建议先在路由器中给服务器设置固定 DHCP 地址，或直接给服务器配置固定局域网 IP。例如服务器地址为 `192.168.1.20`，就在 `.env.production` 中设置：

```dotenv
APP_BIND_IP=192.168.1.20
APP_PORT=80
ONLYOFFICE_PORT=8089
OFFICE_PUBLIC_URL=http://192.168.1.20:8089
```

如果暂时无法确定固定地址，可以保留 `APP_BIND_IP=0.0.0.0`，平台会监听服务器的全部网卡。请确保路由器没有给该端口设置公网端口转发。

## 2. 首次部署

将整个 `xiqin-model-platform` 目录上传到服务器，例如 `/opt/xiqin-model-platform`，然后执行：

```bash
cd /opt/xiqin-model-platform
cp .env.production.example .env.production
chmod 600 .env.production
```

编辑 `.env.production`，替换所有 `CHANGE_ME`。基础设施密码建议直接使用十六进制随机值，避免 URL 特殊字符和转义问题：

```bash
openssl rand -hex 32
openssl rand -hex 64
```

管理员密码只在数据库第一次初始化时生效。建议至少 16 位，并混合大小写字母、数字和符号。

完成环境变量后部署：

```bash
chmod +x scripts/deploy.sh scripts/backup.sh
./scripts/deploy.sh
```

首次构建需要下载 Java、Node.js 和系统依赖，耗时通常比后续升级长。部署成功后，局域网内其他电脑访问：

```text
http://服务器局域网IP/
```

如果 80 或 8089 端口已被占用，可分别修改 `.env.production` 中的 `APP_PORT` 或 `ONLYOFFICE_PORT`；修改 ONLYOFFICE 端口时须同步修改 `OFFICE_PUBLIC_URL`。

## 3. 日常检查

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml ps
docker compose --env-file .env.production -f docker-compose.prod.yml logs -f --tail=200
```

只查看某个服务，例如后端或模型转换服务：

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml logs -f backend
docker compose --env-file .env.production -f docker-compose.prod.yml logs -f worker
```

## 4. 备份

执行：

```bash
./scripts/backup.sh
```

脚本会把 PostgreSQL 数据和 MinIO 模型文件保存到 `backups/时间戳/`，并生成校验文件。MinIO 文件通过其对象接口导出，不会直接复制运行中的数据卷。应再将该目录同步到另一台服务器或对象存储，避免备份与生产数据同时损坏。

建议频率：

- PostgreSQL：每天一次
- MinIO 模型文件：每天增量或至少每周完整备份
- 每次版本升级前额外备份一次

恢复会覆盖现有数据，因此没有提供自动恢复脚本。需要恢复时，先停止平台并确认目标备份，再执行人工恢复。

## 5. 更新版本

上传新代码后：

```bash
cd /opt/xiqin-model-platform
./scripts/backup.sh
./scripts/deploy.sh
```

生产数据保存在具名 Docker 卷中，重新构建镜像不会删除数据。不要使用 `docker compose down -v`，其中 `-v` 会删除数据卷。

## 6. 局域网访问控制

纯局域网使用不要求域名和 HTTPS。建议同时做好以下限制：

- 不在路由器中设置公网端口映射或 DMZ 主机
- 服务器防火墙只允许本地网段访问平台端口，例如 `192.168.1.0/24`
- 管理员密码和 `.env.production` 仍应按生产环境强度设置
- 需要远程访问时使用公司 VPN，不直接把平台端口暴露到公网

## 7. 数据位置

生产数据使用以下固定卷名：

| 数据 | Docker 卷 |
|---|---|
| PostgreSQL | `xiqin_postgres_data` |
| Redis | `xiqin_redis_data` |
| RabbitMQ | `xiqin_rabbitmq_data` |
| MinIO 模型与附件 | `xiqin_minio_data` |
| ONLYOFFICE 配置 | `xiqin_onlyoffice_data` |
| ONLYOFFICE 日志 | `xiqin_onlyoffice_logs` |
| ONLYOFFICE 文档缓存 | `xiqin_onlyoffice_cache` |

网页端对局域网开放 `APP_PORT`，文档服务开放 `ONLYOFFICE_PORT`。5432、6379、5672、9000、9001、15672 和 8080 均不会直接暴露。
