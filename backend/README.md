# backend

鞋厂订单与打印助手系统第一阶段后端服务。

## 功能范围

- 上传 Excel 或图片订单原稿。
- 上传时只保存原始文件并识别订单，不立即生成 PDF。
- Excel 解析“订单”sheet，识别订单号、客户、款号、颜色、数量、箱数、交期。
- 图片生成待手动补充订单记录。
- 点击打印后按 `ORDER` 或 `PACKING` 提取对应 sheet 生成 PDF 预览。
- 用户确认份数后创建打印任务。
- 查询待打印任务、更新任务状态。

## 本地启动

1. 安装 JDK 17、Maven、MySQL 8、LibreOffice。
2. 执行 `sql/init.sql` 和 `sql/print_table.sql`。
3. 修改 `src/main/resources/application.yml` 中的数据库账号和 LibreOffice 命令。
4. 启动后端：

```powershell
cd D:\work\shoe-factory-assistant\backend
$env:JAVA_HOME="D:\develop\JAVA\jdk-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

如果 `soffice` 不在系统 PATH 中，把 `app.file-storage.libre-office-command` 改成完整路径，例如：

```yaml
app:
  file-storage:
    libre-office-command: C:/Program Files/LibreOffice/program/soffice.exe
```
