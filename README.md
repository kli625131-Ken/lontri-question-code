# LONTRI 问题库运维平台

基于现有项目继续收口优化的运维问题平台，主链路保持为：

`导入中心 -> 问题台账 -> 问题详情 -> 项目视图 -> 运营总览`

本轮重点收口为：

- 新数据源基准化
- 页面减法优化
- 轻量纳入问题库四维模型
- Docker 一键部署

## 1. 当前结构

- `backend/`
  Spring Boot 3 + MyBatis-Plus + MySQL
- `frontend/`
  Vue 3 + Vite + Element Plus
- `docs/`
  样板 Excel、业务说明、收口方案
- `docker-compose.yml`
  本地演示 / 快速部署入口

## 2. 数据口径

- 当前唯一基准数据源：`docs/LONTRI项目运维记录.xlsx`
- 问题主单统一落在 `ops_issue`
- 手工新建与 Excel 导入统一落到同一张问题主表
- 项目字段已补齐：
  `customer_name / project_group / project_name / project_code / parent_project_code / project_level / is_active`

## 3. 本地开发

### 后端

```powershell
cd backend
$env:MAVEN_OPTS='-Dmaven.repo.local=d:/code/lontri/questioncode/.m2/repository'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端

```powershell
cd frontend
npm install
npm run dev
```

默认访问：

- 前端：`http://localhost`
- 后端：`http://localhost:8080/api/v1`

默认账号：

- 用户名：`admin`
- 密码：`admin123`

## 4. Docker 一键启动

### 4.1 准备环境变量

```powershell
Copy-Item .env.example .env
```

如需直接重置旧数据并重新导入样板 Excel，可把 `.env` 中的：

```env
OPS_IMPORT_RESET_ON_BOOTSTRAP=true
```

### 4.2 启动

```powershell
docker compose up -d --build
```

### 4.3 访问

- 前端：`http://localhost`
- 后端健康检查：`http://localhost:8080/actuator/health`

### 4.4 停止

```powershell
docker compose down
```

如果要连同数据库数据卷一起清空：

```powershell
docker compose down -v
```

## 5. 数据初始化 / 导入策略

后端启动时会自动执行：

1. 建表和字段补齐
2. 初始化管理员账号
3. 按环境变量决定是否重置业务数据
4. 按环境变量决定是否用基准 Excel 自动导入

关键环境变量：

- `OPS_IMPORT_BOOTSTRAP_ENABLED`
  是否自动导入基准 Excel
- `OPS_IMPORT_BOOTSTRAP_FILE`
  基准 Excel 文件名
- `OPS_IMPORT_RESET_ON_BOOTSTRAP`
  启动时是否先清空旧业务数据
- `SPRING_DATASOURCE_URL`
  如需切到外部数据库可直接覆盖

## 6. 方案说明

本轮收口方案文档见：

- [docs/LONTRI本轮收口优化方案.md](/d:/code/lontri/questioncode/docs/LONTRI本轮收口优化方案.md)
