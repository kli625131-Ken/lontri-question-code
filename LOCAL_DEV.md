# 完全本地开发环境

目标：前端、后端、本机 MySQL 都在本机运行；Docker 只用于一次性导出现有数据库数据，日常开发不依赖 Docker。

## 1. 环境要求

- JDK 17
- Maven
- Node.js 20 或兼容版本
- MySQL 8.0，本机端口：`3306`，并且 `mysql.exe` 已加入 PATH
- 如需迁移 Docker Desktop 里的旧数据，需要 Docker Desktop 可启动

默认开发地址：

- 前端：http://localhost:5173
- 后端：http://localhost:8080/api/v1
- 健康检查：http://localhost:8080/actuator/health
- MySQL：localhost:3306
- 数据库：`problem_db`
- 应用账号：`lontri`
- 应用密码：使用你本机 `.env` 中的 `MYSQL_PASSWORD`

默认登录账号：

- 用户名：`admin`
- 密码：`admin123`

## 2. 首次准备本机 MySQL

如果你不需要迁移 Docker 里的旧数据，只需要创建库和账号：

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS problem_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'lontri'@'localhost' IDENTIFIED BY 'change_me_app_password'; CREATE USER IF NOT EXISTS 'lontri'@'127.0.0.1' IDENTIFIED BY 'change_me_app_password'; ALTER USER 'lontri'@'localhost' IDENTIFIED BY 'change_me_app_password'; ALTER USER 'lontri'@'127.0.0.1' IDENTIFIED BY 'change_me_app_password'; GRANT ALL PRIVILEGES ON problem_db.* TO 'lontri'@'localhost'; GRANT ALL PRIVILEGES ON problem_db.* TO 'lontri'@'127.0.0.1'; FLUSH PRIVILEGES;"
```

后端启动时会自动建表、补字段、初始化管理员账号。默认不会清空旧业务数据。

## 3. 从 Docker MySQL 迁移数据到本机 MySQL

当前 Docker Compose 的 MySQL 容器名是 `lontri-mysql`，数据库是 `problem_db`。

推荐使用脚本迁移：

```powershell
.\scripts\migrate-docker-mysql-to-local.ps1 -LocalPort 3306
```

如果 Docker MySQL 的 root 密码不是 `.env` 中的值，增加参数：

```powershell
.\scripts\migrate-docker-mysql-to-local.ps1 -LocalPort 3306 -DockerRootPassword '你的Docker数据库root密码'
```

脚本会执行：

1. 启动 Docker Compose 中的 `mysql` 服务。
2. 停止 Docker Compose 中的 `backend` / `frontend` 服务，避免导出时还有写入。
3. 在容器内执行 `mysqldump`，避免 PowerShell 重定向导致中文编码问题。
4. 把 dump 文件复制到本地 `backup/` 目录。
5. 在本机 MySQL 创建 `problem_db` 和 `lontri` 账号。
6. 导入 dump 到本机 MySQL。

导入会覆盖本机 `problem_db` 中同名表的数据。脚本导入前会要求输入 `IMPORT` 二次确认。

如果不想每次输入本机 MySQL root 密码，可以先设置环境变量：

```powershell
$env:LOCAL_MYSQL_ROOT_PASSWORD='你的本机root密码'
.\scripts\migrate-docker-mysql-to-local.ps1 -LocalPort 3306
```

只导出 Docker 数据，不导入本机 MySQL：

```powershell
.\scripts\migrate-docker-mysql-to-local.ps1 -ExportOnly
```

使用已有 dump 文件导入：

```powershell
.\scripts\migrate-docker-mysql-to-local.ps1 -SkipExport -DumpFile .\backup\problem_db_xxxxx.sql -LocalPort 3306
```

## 4. 启动完全本地开发环境

本机 MySQL 已启动后，执行：

```powershell
.\scripts\start-local-dev.ps1 -NoDocker -MysqlPort 3306
```

脚本会执行：

1. 停止上一次本地启动的 backend / frontend 进程。
2. 本地启动 Spring Boot 后端，连接 `localhost:3306/problem_db`。
3. 本地启动 Vite 前端。
4. 等待后端健康检查和前端页面可访问。

日志位置：

```text
.local-dev/backend.out.log
.local-dev/backend.err.log
.local-dev/frontend.out.log
.local-dev/frontend.err.log
```

## 5. 停止本地开发环境

```powershell
.\scripts\stop-local-dev.ps1
```

该命令只停止本地前端和后端进程，不会停止本机 MySQL。

## 6. 手动启动方式

后端：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE='dev'
$env:SERVER_PORT='8080'
$env:SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/problem_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:SPRING_DATASOURCE_USERNAME='lontri'
$env:SPRING_DATASOURCE_PASSWORD='change_me_app_password'
mvn clean spring-boot:run "-Dspring-boot.run.profiles=dev"
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

## 7. 验证

检查后端：

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
```

检查页面：

```text
http://localhost:5173
```

登录后重点检查：

- 运营总览是否有数据
- 问题台账是否有数据
- 项目视图是否有数据
- 导入中心历史批次是否正常

## 8. 常见问题

### 端口被占用

检查端口：

```powershell
Get-NetTCPConnection -LocalPort 3306,8080,5173 -State Listen -ErrorAction SilentlyContinue
```

当前前端代理固定转发到 `http://localhost:8080`，所以后端开发端口建议保持 `8080`。

### 后端连不上 MySQL

先确认本机 MySQL 可登录：

```powershell
mysql -ulontri -pchange_me_app_password -hlocalhost -P3306 problem_db -e "SELECT 1;"
```

如果失败，优先检查：

- MySQL 服务是否启动
- 端口是否是 `3306`
- `lontri` 用户是否存在
- `problem_db` 是否存在
- 密码是否与本机 `.env` 中的 `MYSQL_PASSWORD` 一致

### 不要直接复制 Docker 的 MySQL 数据目录

不要直接复制 Docker volume 里的 `/var/lib/mysql` 到本机 MySQL。不同 MySQL 小版本、文件权限、数据字典都可能导致启动失败。用 `mysqldump` 导出再导入更稳。

## 9. Docker 部署模式

本地开发完成后，仍然可以使用原 Docker 部署方式：

```powershell
docker compose up -d --build
```
