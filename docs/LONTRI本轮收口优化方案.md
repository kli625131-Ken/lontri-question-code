# LONTRI 本轮收口优化方案

## 1. 页面优化清单

- 运营总览：
  只保留问题总量、待处理、处理中、待确认导入 4 个 KPI；中区保留最近问题与当前导入批次；项目状态改成客户/项目组轻量汇总。
- 项目视图：
  项目卡片只保留项目名、项目编码、开口问题数、提醒阈值、状态标签；上层汇总改为客户/项目组简表；下半区聚焦当前项目概览、联系人、质保信息。
- 导入中心：
  强化样板项目导入说明；优先展示当前批次、总行数、待确认、可提交；待确认行编辑表格保留为主操作区；Sheet/行类型/清洗统计下沉到页面底部。
- 问题台账：
  筛选区压缩为两行；列表聚焦问题编号、项目、反馈人、分类、收到反馈时间、事项、处理人、状态、最新进展、完成情况、来源；手工新建流程改成“问题定义 + 当前处理信息”。
- 问题详情：
  调整为四段式结构：问题定义、原因归因、处置过程、预防沉淀。

## 2. 项目分类落地方案

- 当前项目主数据统一补齐字段：
  `customer_name`
  `project_group`
  `project_name`
  `project_code`
  `parent_project_code`
  `project_level`
  `is_active`
- 当前阶段问题主单仍绑定最细一级 `project`。
- 客户与项目组先通过 `customer_name`、`project_group` 做汇总，不继续扩张为复杂主数据管理页。
- 导入时按项目名自动归并客户和项目组口径，保证后续总览页和项目页可以按上层做聚合统计。

## 3. 问题主表与创建流程实现方案

### 问题主表

当前已围绕以下字段统一主表口径：

- `issue_no`
- `project_id`
- `reporter_name`
- `category_path`
- `received_at`
- `item_title`
- `owner_name`
- `current_status`
- `latest_progress`
- `completion_status`
- `completed_at`
- `notes`
- `source_type`
- `source_batch_id`
- `source_sheet`
- `source_row_number`

并预留四维模型轻量字段：

- `cause_category`
- `cause_detail`
- `preventive_action`
- `follow_up_action`

### 创建流程

- 手工新建：
  选择项目 -> 填问题定义 -> 填当前处理信息 -> 保存问题主单 -> 跳转问题详情。
- Excel 导入：
  上传 Excel -> 生成批次和待确认行 -> 人工确认 -> 提交入库 -> 自动生成问题主单。
- 两种入口最终统一落到 `ops_issue`。

## 4. 问题库四维模型映射方案

- 问题定义：
  落在问题台账字段与详情页“问题定义”区块。
- 原因归因：
  落在详情页 `cause_category / cause_detail`。
- 处置过程：
  落在详情页状态、最新进展、处理记录、时间线、闭环/重开动作。
- 预防沉淀：
  落在详情页 `notes / preventive_action / follow_up_action`。

## 5. 数据重置与新数据导入方案

- 新的 `docs/LONTRI项目运维记录.xlsx` 作为唯一基准数据源。
- 后端新增 `ops.import.reset-on-bootstrap` 开关。
- 当该开关为 `true` 时，启动流程会先清空：
  问题主单
  问题记录
  导入批次
  导入待确认行
  项目联系人
  质保信息
  分类字典
  项目和项目授权
- 重置后再按基准 Excel 自动导入，避免旧脏数据继续影响展示。

## 6. Docker 一键部署方案

- 新增根目录 `docker-compose.yml`，统一启动：
  MySQL
  Backend
  Frontend
- 新增：
  `backend/Dockerfile`
  `frontend/Dockerfile`
  `backend/src/main/resources/application-prod.yml`
  根目录 `.env.example`
- 启动时支持通过环境变量切换：
  数据库连接
  是否自动导入样板 Excel
  是否启动即重置旧数据
  默认提醒阈值
- 数据初始化方案：
  Spring Boot 启动建表 + 管理员种子 + Excel bootstrap import。

## 7. README 启动步骤

### 本地开发

1. 启动 MySQL 并创建 `problem_db`
2. 运行后端 `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
3. 运行前端 `npm run dev`

### Docker 一键部署

1. `Copy-Item .env.example .env`
2. 按需修改 `.env`
3. `docker compose up -d --build`
4. 浏览器访问 `http://localhost`

### 若要清旧数据并重新导入基准 Excel

1. 将 `.env` 中 `OPS_IMPORT_RESET_ON_BOOTSTRAP=true`
2. 重新执行 `docker compose up -d --build`
