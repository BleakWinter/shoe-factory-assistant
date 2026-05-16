# 鞋厂订单打印助手

这是一个给家里鞋厂使用的订单打印辅助系统。当前版本主要解决一个很具体的问题：爸妈不会调整 Excel 打印格式，而不同电脑、不同 Excel 版本经常导致本来一页能打印的订单被拆成很多页。

系统目标是让他们只需要上传订单 Excel，然后在网页里点按钮预览和打印。V1 不追求复杂的工厂管理系统，先把“上传订单、解析明细、生成订单/装箱单打印预览”这条流程做好。

## 当前已实现功能

### 1. 订单 Excel 上传

- 在“打印列表”页面上传订单 Excel。
- 支持 `.xlsx`、`.xls`。
- 上传后保存原始 Excel 文件。
- 上传时不会马上生成 PDF，只有点“打印”并选择类型时才生成预览。
- 每上传一个订单文件，会创建一条待打印记录，状态默认为 `PENDING`。

当前原始 Excel 保存路径：

```text
D:\清化资料\年份\达维\月份
```

例如：

```text
D:\清化资料\2026\达维\5月\26055-JC-Nordtrom-JCD-253.401款444对订单（清化) 4.20更新装箱单.xlsx
```

### 2. 订单明细解析

系统会读取 Excel 里的 `订单` sheet。

当前解析规则：

- 自动在 `订单` sheet 前 30 行里寻找明细表头行。
- 表头行通常包含 `图片`、`楦头`、`开发编号`、`客人` 等字段。
- 找到表头后，从表头下一行开始作为订单明细。
- 遇到合计行后停止解析。
- 如果中间再次出现一行表头，会跳过，不会当作订单明细。
- 图片按 Excel 图片锚点关联到对应明细行。
- 款号数量不再写死到 `AO` 列，而是按表头动态识别。

已解析字段包括：

- 订单流水号
- 图片
- 楦头
- 开发编号
- 客人
- 客人订单号
- 出货时间
- PO
- 客人型体号
- 英文颜色
- 英文材质
- 面料
- 里料/垫脚
- 饰扣/鞋带
- 中底/包中底
- 大底
- 商标
- 尺码数量
- 双数
- 箱数
- 开始箱号
- 结束箱号
- 总数量
- 出货状态

尺码数量的规则：

- 从 `商标` 后面的列开始读取尺码。
- 一直读到 `双数`、`箱数`、`开始箱号`、`结束箱号`、`总数量` 这些汇总列前面。
- 尺码表头写什么就保存什么，比如 `5`、`5.5`、`6`、`5/6`、`6-6.5` 都可以。
- 数据库存成 JSON，例如：

```json
{
  "5": 1,
  "5.5": 2,
  "6": 3
}
```

Excel 内嵌图片保存路径：

```text
D:\清化资料\image\年份\月份\订单号
```

例如：

```text
D:\清化资料\image\2026\5月\26055
```

后续如果爸妈单独上传用于打印替换的图片，预留路径是：

```text
D:\清化资料\image\swap
```

### 3. 订单列表页面

前端页面：`/orders`

主要用途是查看 Excel 解析出来的明细行。

已实现：

- 展示订单流水号、图片、楦头、开发编号、材料、尺码数量等字段。
- 支持分页。
- 支持图片预览。
- 支持条件过滤：
  - 订单流水号
  - 款号，也就是开发编号
  - 楦头
  - 出货状态
  - 出货时间
- 出货状态当前包括：
  - `NOT_SHIPPED`：未出货
  - `SHIPPED`：已出货

注意：当前“款号”过滤使用的是开发编号，例如 `JCD-253-9-02`，不是楦头 `JCD-253`。

### 4. 打印列表页面

前端页面：`/tasks`

这是爸妈主要使用的页面。

已实现：

- 上传订单 Excel。
- 展示每个上传订单对应的一条待打印记录。
- 展示字段：
  - 订单流水号
  - 客户
  - 开发编号
  - 订单总对数
  - 状态
  - 上传时间
  - 操作
- 点击“打印”后打开弹窗。
- 弹窗里可以选择：
  - 订单
  - 装箱单
- 选择后生成 PDF 预览。
- 使用浏览器/PDF 预览窗口自带打印功能进行打印。
- “重新上传”按钮已占位，当前还没有实现具体逻辑。

### 5. PDF 打印预览

当前支持两种打印类型：

- `ORDER`：使用 Excel 的 `订单` sheet。
- `PACKING`：使用 Excel 的 `装箱单` sheet。

生成 PDF 前，后端会自动设置打印格式：

- A4。
- 横向。
- 横向压到一页宽。
- 纵向允许多页。
- 小页边距。
- 居中打印。
- 打印区域覆盖实际内容区域。

这样可以避免 Excel 版本不同导致横向列被拆成多张纸。

PDF 生成方式：

1. 优先尝试 LibreOffice 命令 `soffice`。
2. 如果电脑没有 LibreOffice，会尝试 Windows 上的 Microsoft Excel COM 导出 PDF。

当前 V1 不直接调用真实打印机 API，只提供 PDF 预览和浏览器打印。

### 6. 旧数据和旧路径迁移

后端启动时会执行一个轻量迁移逻辑：

- 修正 `print_task.preview_id` 允许为空。
- 修正 `print_task.print_type` 允许为空。
- 如果 `order_line` 缺少 `shipment_status` 字段，则自动新增。
- 把旧的 Excel 文件从用户目录迁移到 `D:\清化资料\年份\达维\月份`。
- 把旧的订单图片迁移到 `D:\清化资料\image\年份\月份\订单号`。
- 同步更新数据库里的路径。

这部分主要是为了兼容开发早期保存到旧目录的数据。

## 当前没有实现的功能

V1 暂时没有实现这些功能：

- 用户登录和权限。
- 真正连接打印机自动打印。
- 重新上传订单并覆盖旧订单。
- 手动上传替换图片并参与打印。
- 订单进度表。
- 材料采购、库存、生产跟踪。
- 手机端专门优化的进度管理页面。
- 多工厂、多员工协作。

这些可以作为后续版本继续做。

## 项目结构

```text
shoe-factory-assistant
├── backend       Spring Boot 后端
├── web           React + Vite 前端
├── print-agent   后续本地打印代理，当前基本未实现
├── sql           MySQL 表结构脚本
└── docs          早期拆分文档
```

## 技术架构

### 后端

目录：`backend`

主要技术：

- Java 17
- Spring Boot 3.3.5
- Spring Web
- Spring Validation
- MyBatis-Plus
- MySQL
- Apache POI
- LibreOffice / Microsoft Excel PDF 导出

主要职责：

- 接收 Excel 上传。
- 保存原始文件。
- 解析 Excel 订单明细。
- 提取 Excel 内嵌图片。
- 写入订单、明细、打印任务数据。
- 按需生成订单或装箱单 PDF 预览。
- 提供图片和 PDF 访问接口。

### 前端

目录：`web`

主要技术：

- React 18
- Vite 6
- TypeScript
- Ant Design
- Axios
- React Router

主要页面：

- `/orders`：订单明细列表。
- `/tasks`：打印任务列表和上传入口。

### 数据库

数据库：MySQL

主要表：

- `source_file`：上传的源文件。
- `order_record`：订单主记录。
- `order_line`：订单明细行。
- `print_preview`：PDF 预览记录。
- `print_task`：打印任务记录。

初始化脚本：

```text
sql/print_table.sql
```

## 主要接口

后端默认地址：

```text
http://localhost:8080
```

前端默认地址：

```text
http://localhost:5173
```

### 上传订单 Excel

```http
POST /api/orders/upload
Content-Type: multipart/form-data
```

字段名：

```text
file
```

说明：

- 保存 Excel 原文件。
- 解析 `订单` sheet。
- 写入订单明细。
- 创建一条待打印任务。

### 查询订单明细

```http
GET /api/orders/lines
```

查询参数：

- `orderNo`
- `styleNo`
- `lastNo`
- `shipmentStatus`
- `deliveryDate`
- `page`
- `size`

### 查看订单明细图片

```http
GET /api/orders/lines/{id}/image
```

用于前端展示 Excel 里提取出的鞋图。

### 查询打印任务列表

```http
GET /api/print-tasks
```

用于打印列表页面。

### 生成打印任务 PDF 预览

```http
POST /api/print-tasks/{id}/preview
Content-Type: application/json
```

请求体：

```json
{
  "printType": "ORDER"
}
```

或者：

```json
{
  "printType": "PACKING"
}
```

### 打开 PDF 预览

```http
GET /api/print-previews/{id}/preview
```

### 更新打印任务状态

```http
PATCH /api/print-tasks/{id}/status
Content-Type: application/json
```

请求体示例：

```json
{
  "status": "SUCCESS"
}
```

## 本地运行

### 1. 准备环境

需要安装：

- JDK 17
- Maven
- Node.js
- MySQL
- LibreOffice，可选
- Microsoft Excel，可选，Windows 上可作为 PDF 导出兜底

### 2. 初始化数据库

创建数据库后执行：

```text
sql/print_table.sql
```

当前后端配置文件位置：

```text
backend/src/main/resources/application.yml
```

当前数据库配置示例：

```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.75.130:3316/shoe_factory_assistant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 1234
```

文件保存配置：

```yaml
app:
  file-storage:
    root-path: ${user.home}/shoe-factory-assistant/files
    order-archive-root-path: D:/清化资料
    order-archive-customer: 达维
    order-image-archive-root-path: D:/清化资料/image
    print-image-swap-path: D:/清化资料/image/swap
    libre-office-command: soffice
    libre-office-timeout-seconds: 120
    preview-url-prefix: /api/print-previews
```

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端地址：

```text
http://localhost:8080
```

### 4. 启动前端

```powershell
cd web
npm install
npm run dev
```

前端地址：

```text
http://localhost:5173
```

### 5. 构建验证

后端：

```powershell
cd backend
mvn test
```

前端：

```powershell
cd web
npm run build
```

## 使用流程

1. 打开前端 `http://localhost:5173/tasks`。
2. 点击“上传订单 Excel”。
3. 选择订单 Excel。
4. 系统解析订单明细，并在打印列表生成一条记录。
5. 去 `/orders` 可以查看解析出来的每一行订单明细。
6. 回到 `/tasks`，点击对应订单的“打印”。
7. 在弹窗里选择“订单”或“装箱单”。
8. 等待 PDF 预览生成。
9. 在 PDF 预览窗口中使用浏览器打印。

## 样本订单规则

当前主要按这类订单格式适配：

- Excel 有 `订单` sheet。
- Excel 有 `装箱单` sheet。
- `订单` sheet 里有一行明确的明细表头。
- 表头行不要求固定在第 5 行，系统会自动寻找。
- 明细从表头下一行开始。
- 图片位于表头 `图片` 对应列。
- `开发编号` 是订单里的完整款号。
- `楦头` 是类似 `JCD-253`、`JCD-401A` 的楦头号。
- `商标` 后面是尺码列。
- `双数`、`箱数`、`总数量` 是汇总列。

如果以后订单格式变化，优先保证表头名字稳定，这样系统就能继续自动识别。

## 维护说明

- 改后端代码后，需要重启后端。
- 改 `application.yml` 后，也需要重启后端。
- 旧订单如果已经按旧逻辑解析入库，改了解析规则后不会自动重新解析，需要重新上传。
- 如果 PDF 预览失败，优先检查电脑是否有 LibreOffice 或 Microsoft Excel。
- 如果页面提示数据库连接失败，优先检查 MySQL 地址、端口、账号、密码。
- 如果图片不显示，优先检查数据库里的 `image_path` 是否指向真实存在的文件。
