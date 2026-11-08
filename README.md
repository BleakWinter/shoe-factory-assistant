# 鞋厂订单打印助手

这是一个给家里鞋厂使用的订单打印辅助系统。当前版本优先解决订单 Excel 上传、订单/装箱单预览打印、订单明细识别和常用配置维护。

系统目标是让使用者只需要上传订单 Excel，在网页里完成预览、打印、识别和查看明细。V1 不做完整工厂管理系统，先把“上传订单、生成订单/装箱单打印任务、按需识别明细、打印出货单”这条主流程做好。

## 当前已实现

### 1. 订单 Excel 上传

- 在 `/tasks/order` 页面上传订单 Excel。
- 支持 `.xlsx`、`.xls`。
- 上传后保存原始 Excel 文件。
- 上传阶段读取订单基础信息，写入 `order_record`。
- 上传后自动创建两条整单级打印任务：
  - `ORDER`：订单 sheet。
  - `PACKING`：装箱单 sheet。
- 上传后不会马上生成 PDF，点击对应打印任务的“打印”按钮时才生成预览。

原始 Excel 默认保存路径：

```text
D:\清化资料\年份\达维\月份
```

### 2. 订单与装箱单识别

上传后订单先进入待识别状态。订单 sheet 和装箱单 sheet 的 PDF 打印不依赖完整识别，可以直接从原始 Excel 生成。

需要结构化明细时，在 `/orders` 页面手动触发：

- 识别订单：解析 `订单` sheet，写入 `order_record_detail`。
- 识别装箱单：解析 `装箱单` sheet，写入 `order_packing_detail`。

订单识别会提取 Excel 内嵌图片，默认保存到：

```text
D:\清化资料\image\年份\月份\订单号
```

### 3. 打印任务

当前整单级打印任务使用 `order_sheet_print_task` 表。

- 每个订单 Excel 上传后创建订单、装箱单两条任务。
- 重新上传同一订单时复用这两条任务，替换原稿文件并重置打印状态；已识别的订单明细、装箱明细和工序明细保留。
- 任务状态：
  - `PENDING`：待打印。
  - `PRINTED`：已打印。
  - `FAILED`：生成 PDF 或打印失败。
  - `INVALID`：任务已废弃，不再参与打印列表。
- PDF 生成成功后路径写入 `preview_pdf_path`。
- 用户确认打印后，前端调用接口标记为已打印并累计 `print_count`。

PDF 默认保存路径：

```text
D:\清化资料\pdf\年份\月份\订单号
```

### 4. 页面入口

- `/orders`：订单列表，可筛选订单、开发编号、识别状态，并手动识别订单/装箱单。
- `/orders/:id/details`：订单明细页，展示订单明细和匹配的装箱单明细。
- `/statistics`：按开发编号层级统计鞋子双数和明细行数。
- `/monthly-reconciliation`：财务管理 → 月度对账。结算单支持待请款、跨月补结、质保金和领料扣款；出货查询按实际出货月核对明细。
- `/unpriced-styles`：财务管理下的「待定价款号」，与月度对账、价格配置并列；展示尚未配置客户单价的已出货款号，按月份、主款号、完整款号分层，并按客户＋完整款号去重。各层区分「未报价」「报价待完善」「已报价待客户确认」，明细显示最近已报价格、日期、版本和鞋图。已有报价可直接查看或继续编辑，无报价才显示「做报价」；客户确认后采用为客户单价，历史缺价记录同步移除。鞋图点击可放大，支持客户筛选和原始出货记录查看。
- `/tasks/order`：上传订单 Excel，打印订单和装箱单。
- `/tasks/outer-carton-label`：打印外箱贴标。
- `/tasks/inner-box-label`：打印内盒贴标。
- `/tasks/shipping-note`：出货单打印任务列表，可新建、编辑收货信息及增删出货明细、查看明细和预览打印。
- `/component-orders/packing`：订包装。
- `/component-orders/outsole`：订大底。
- `/component-orders/insole`：订中底。
- `/component-orders/heel`：订鞋跟。

订包装的装箱容量汇总规则见 [`docs/订包装业务规则.md`](docs/订包装业务规则.md)。
- `/style-configs`：盒规、净重、毛重配置。
- `/price-configs`：位于“财务管理”的价格配置，包含“客户标价”和“报价管理”两个页签，与“月度对账”共用导航分组。
- `/price-configs/quotations`：价格配置下的“报价管理”页签；在系统内编辑材料、加工费、保存版本、预览及导出，确认后采用为客户单价。使用和升级说明见 [鞋款报价](docs/鞋款报价.md)。

### 5. PDF 预览

当前支持两种整单级 PDF：

- `ORDER`：读取 Excel 的 `订单` sheet。
- `PACKING`：读取 Excel 的 `装箱单` sheet。

生成 PDF 前，后端会设置：

- A4。
- 横向。
- 横向压到一页宽。
- 纵向允许多页。
- 小页边距。
- 居中打印。
- 打印区域裁到业务内容列。

PDF 转换优先使用 LibreOffice `soffice`，Windows 上可用 Microsoft Excel COM 导出兜底。

## 项目结构

```text
shoe-factory-assistant
├── backend       Spring Boot 后端
├── web           React + Vite 前端
├── print-agent   后续本地打印代理
├── sql           MySQL 建表和迁移脚本
└── docs          技术、接口、部署文档
```

## 技术栈

后端：

- Java 17
- Spring Boot 3.3.5
- MyBatis-Plus
- MySQL
- Apache POI
- LibreOffice / Microsoft Excel PDF 导出

前端：

- React 18
- Vite 6
- TypeScript
- Ant Design
- Axios
- React Router

## 数据库

### 多客户录单、统计与对账

达为继续使用 Excel 上传识别；宝翔、天兔子及其他手工客户可在订单页手工录单、勾选明细全量确认出货，统一参与按客户的数据统计和月度对账。价格按客户和款号分别维护，出货查询使用当前价，已确认的结算单保留当时的价格和金额。

部署本次功能前，先备份数据库并执行 `sql/add_multi_customer_ledger.sql`，再更新后端和前端。新库先执行 `sql/print_table.sql`，再执行此升级脚本。详细操作、价格口径和验证方式见 [多客户录单与对账](docs/多客户录单与对账.md)。

初始化数据库：

```sql
CREATE DATABASE IF NOT EXISTS shoe_factory_assistant
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

建表脚本：

```text
sql/print_table.sql
```

主要表：

- `order_record`：订单主记录。
- `order_record_detail`：订单 sheet 明细。
- `order_packing_detail`：装箱单明细。
- `order_detail_process`：订包装、订大底、订中底等处理状态。
- `order_sheet_print_task`：订单/装箱单整单级打印任务，保存原稿文件名、本地路径、PDF 路径和打印状态。
- `shoe_style_config`：盒规、净重、毛重配置（保留旧鞋价字段）。
- `customer_price_config`：当前客户 / 款号单价。
- `shoe_quotation`、`shoe_quotation_item`：鞋款报价版本与成本项目，启用前执行 `sql/add_shoe_quotation.sql`。
- `monthly_settlement`：结算草稿、确认快照与到账日期，启用前执行 `sql/add_monthly_settlement.sql`。跨月补结从系统已确认的账单带出，历史 Excel 款项按实际确认金额核对后录入。
- `shipping_note_task`：出货单打印任务主表。
- `shipping_note_task_item`：出货单打印任务明细快照。
- `packing_order_task`：订包装专用主表。
- `packing_order_task_item`：按订单与款号汇总的包装规格、装法箱数及来源引用。
- `component_order_task`：现有配件模块的通用任务主表。
- `component_order_task_item`：配件下单任务明细。

旧库升级可参考：

```text
sql/add_order_sheet_print_task.sql
sql/move_original_file_to_sheet_print_task.sql
sql/add_shipping_note_task.sql
sql/add_component_order_task.sql
sql/add_packing_order_task.sql
sql/add_inner_box_type.sql
```

订包装启用前，在实际使用的业务数据库依次执行 `sql/add_packing_order_task.sql` 和 `sql/add_inner_box_type.sql`，创建订包装专用表及内盒类型目录，并补齐款号默认类型与历史类型名称字段。配置中心的“内盒类型”按版本维护名称、备注与启用状态；盒规配置可为每个款号选择默认类型。脚本可重复执行，不删除或覆盖已有记录。包装页面使用独立接口与表结构，规格、备注和装箱汇总按保存时的值保留；同一订单同款的来源明细合并，装箱行去重计算。其他三个配件模块本次不扩展。

## 主要接口

开发环境后端默认地址：

```text
http://localhost:8081
```

上传订单 Excel：

```http
POST /api/orders/upload
Content-Type: multipart/form-data
```

在已有流水号下追加一份独立订单：

```http
POST /api/orders/{id}/append
Content-Type: multipart/form-data
```

查询订单主表：

```http
GET /api/orders
```

手动识别：

```http
POST /api/orders/{id}/recognize-order
POST /api/orders/{id}/recognize-packing
```

查询明细：

```http
GET /api/orders/{id}/details
GET /api/orders/{id}/packing-details
```

打开明细图片：

```http
GET /api/orders/details/{detailId}/image
GET /api/orders/packing-details/{detailId}/image
```

查询打印任务：

```http
GET /api/print-tasks
GET /api/print-tasks/pending?limit=20
```

生成和打开 PDF：

```http
POST /api/print-tasks/{taskId}/preview
POST /api/print-tasks/{taskId}/preview/regenerate
GET /api/print-tasks/{taskId}/pdf
```

标记打印状态：

```http
PATCH /api/print-tasks/{taskId}/printed
PATCH /api/print-tasks/{taskId}/status
```

出货单打印任务：

```http
POST /api/shipping-note-tasks
GET /api/shipping-note-tasks
GET /api/shipping-note-tasks/{id}
PUT /api/shipping-note-tasks/{id}
DELETE /api/shipping-note-tasks/{id}
```

编辑出货单时，`PUT` 可提交 `recipientName`、`shippingDate` 和 `items`（每项包含 `sourceDetailId`）。提交 `items` 会替换该出货单的明细，按提交顺序保存，并重新计算发票编号、开发编号、明细行数、双数和件数；保留原出货单编号及创建时间。明细至少保留一条，且必须有匹配的装箱单。省略 `items` 时只更新收货信息。

重新选择订单时会同步更新出货单的客户归属，同一出货单不能混选不同客户。此编辑入口用于 Excel 打印任务；手工订单及手工出货记录仍从订单页面处理。保存后的统计、月度对账和打印预览均使用更新后的明细，无需增加数据库字段。

出货单列表支持确认后删除。`DELETE` 在同一事务内删除指定出货单及其明细，并清除不再关联任何出货单的明细的“出货单打印”处理记录（类型 7）；仍被其他出货单使用的明细保留该记录。原始订单、订单明细、装箱单、内盒及外箱贴标处理记录均保留；出货统计、月度对账及“隐藏已生成出货单”的结果随之更新。手工出货记录不能从此接口删除。

更多接口见 [docs/接口文档.md](docs/接口文档.md)。

## 本地运行

### 1. 准备环境

需要安装：

- JDK 17
- Maven
- Node.js 18+
- MySQL 8+
- LibreOffice，可选
- Microsoft Excel，可选，Windows 上作为 PDF 导出兜底

如果本机默认 `java -version` 还是 Java 8，请先切到 JDK 17：

```powershell
$env:JAVA_HOME="D:\develop\JAVA\jdk-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
```

### 2. 初始化数据库

执行：

```text
sql/init.sql
sql/print_table.sql
```

修改数据库配置：

```text
backend/src/main/resources/application-dev.yml
backend/src/main/resources/application-prod.yml
```

### 3. 环境选择

后端现在按 Spring Profile 分为 `dev` 和 `prod` 两套配置：

- `dev`：读取 `backend/src/main/resources/application-dev.yml`，当前数据库为 `shoe_factory_assistant_dev`。
- `prod`：读取 `backend/src/main/resources/application-prod.yml`，当前数据库为 `shoe_factory_assistant_prod`。

命令行使用 Maven 启动时，`dev` 是默认 profile：

```powershell
cd D:\work\shoe-factory-assistant\backend
mvn spring-boot:run
```

如果要启动生产配置：

```powershell
cd D:\work\shoe-factory-assistant\backend
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 4. 启动后端

```powershell
cd D:\work\shoe-factory-assistant\backend
$env:JAVA_HOME="D:\develop\JAVA\jdk-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

后端地址：

```text
http://localhost:8081
```

### 5. 在 IDEA 里启动

后端推荐用 IDEA 的 Spring Boot Run Configuration 启动：

1. 用 IDEA 打开项目根目录，确认 `backend/pom.xml` 被识别为 Maven 项目。
2. Project SDK 选择 JDK 17。
3. 新建 Spring Boot 配置，Main class 选择 `com.shoefactory.assistant.ShoeFactoryAssistantApplication`。
4. Use classpath of module 选择后端模块，一般是 `shoe-factory-backend`。
5. Working directory 填项目后端目录，例如 `D:\work\shoe-factory-assistant\backend`。
6. Active profiles 填 `dev` 或 `prod`。

如果 IDEA 没有 Active profiles 输入框，就在 Program arguments 填：

```text
--spring.profiles.active=dev
```

切到生产配置时改成：

```text
--spring.profiles.active=prod
```

也可以在 VM options 里使用：

```text
-Dspring.profiles.active=dev
```

前端可以直接在 IDEA Terminal 启动，也可以新建 npm Run Configuration：

- package.json：`web/package.json`
- scripts：`dev`
- 启动后访问：`http://localhost:5173`

前端的 Vite 代理会把 `/api` 转发到 `http://localhost:8081`，所以本地联调时先启动后端，再启动前端。

### 6. 启动前端

```powershell
cd D:\work\shoe-factory-assistant\web
npm install
npm run dev
```

前端地址：

```text
http://localhost:5173
```

## 构建验证

后端：

```powershell
cd D:\work\shoe-factory-assistant\backend
$env:JAVA_HOME="D:\develop\JAVA\jdk-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
```

前端：

```powershell
cd D:\work\shoe-factory-assistant\web
npm run build
```

## 维护说明

- 改后端代码后需要重启后端。
- 改 `application.yml`、`application-dev.yml` 或 `application-prod.yml` 后需要重启后端。
- 上传后的旧订单不会因为解析规则变化自动重跑，需要重新识别或重新上传。
- PDF 预览失败时，优先检查 LibreOffice 或 Microsoft Excel 是否可用。
- 页面提示数据库连接失败时，优先检查 MySQL 地址、端口、账号、密码。
- 图片不显示时，优先检查数据库里的本地图片路径是否真实存在。
