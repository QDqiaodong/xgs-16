# 写字楼办公桌椅工位绑定管理系统

## 项目简介

写字楼办公桌椅与工位绑定管理系统，支持家具档案、楼层分组、工位绑定、反查、绑定记录追踪，以及楼层搬迁交接台（部门搬迁批次化、逐项校验、并发安全执行与失败重试）。

## 楼层搬迁交接台

行政人员可把一次部门搬迁整理为独立批次，而不是逐件改绑定：

- **批次管理**：选择迁出/迁入楼层并加入现有家具，为每件家具指定目标工位、使用人、部门；同一家具不能同时处于两个未结束批次（数据库在途占用锁强约束），目标工位批次内不可重复。
- **状态机**：待确认 → 可执行 → 执行中 → 已完成 / 部分失败；已撤销终态。创建时的原绑定快照逐件保留，撤回确认后仍可追溯。
- **确认前逐项校验**：家具已删除、原绑定快照已变化、目标工位被批次外家具占用、目标工位与迁入楼层不匹配等均逐项列出，有冲突不得进入可执行。
- **执行**：逐件独立事务写入新工位/使用人/部门/楼层，并在变更台账写入 `RELOCATE` 记录（带批次号）；成功条目不回滚，部分失败后只能重试失败项。
- **并发与重试**：Redis 执行锁 + 批次状态 CAS + 条目/家具行锁 + 台账幂等唯一索引（`relocation_batch_no + furniture_id`）；重复点击、超时重试、刷新都不会重复搬迁或重复生成台账。已被其他操作搬到同一目标的条目按幂等成功收口，目标仍被占用的条目保留失败原因。
- **可观测**：批次列表/详情/状态筛选、逐项结果与失败原因、执行中自动轮询；台账页支持按搬迁批次号筛选。

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
