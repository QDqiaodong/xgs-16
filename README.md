# 写字楼办公桌椅工位绑定管理系统

## 项目简介

写字楼办公桌椅与工位绑定管理系统，支持家具档案、楼层分组、工位绑定、反查和绑定记录追踪。

## 技术栈

- 前端：Vue 3、Vite 5、Element Plus、Pinia、ECharts、Axios
- 后端：Spring Boot 3、JDK 17、Spring Data JPA、Spring Data Redis、Maven
- 数据：MySQL 8、Redis 7
- 部署：Docker Compose、Nginx

## 端口说明

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3216 或 http://127.0.0.1:3216 |
| 后端 API | http://127.0.0.1:8216/api |
| MySQL | 127.0.0.1:3516 |
| Redis | 127.0.0.1:6516 |

端口来自根目录 `.env`，Docker 端口只绑定 `127.0.0.1`。

## 启动方式

```bash
cd xgs-16
docker compose up -d --build
```

本地拆分验证：

```bash
cd backend
mvn compile -q

cd ../frontend
npm ci
npm run build
```

## Docker 构建说明

Compose 使用 `.env` 中的镜像和端口变量构建 backend、frontend，并启动 MySQL 与 Redis：

```bash
docker compose up -d --build
docker compose ps
```

## 常见问题

- Docker 镜像拉取失败：检查 `.env` 的 `DOCKER_REGISTRY` 和 Docker registry mirror。
- 页面接口失败：构建通过后检查 backend 容器、数据库连接和 Nginx `/api` 代理。
- 端口占用：修改 `.env` 中对应端口后重新启动。
