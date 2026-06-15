# backend

鞋厂订单与打印助手系统第一阶段后端服务。

## 功能范围

- 上传 Excel 订单原稿。
- Excel 解析“订单”sheet，写入 `order_record` 和 `order_record_detail`。
- `order_detail_process` 保存每条明细的处理状态。
- 点击打印后按 `ORDER` 或 `PACKING` 提取对应 sheet 生成 PDF 预览。
- PDF 固定保存到 `D:/清化资料/pdf`，路径回写到 `order_record`。
- 打印列表当前直接展示 `order_record`。

## 本地启动

1. 安装 JDK 17、Maven、MySQL 8、LibreOffice。
2. 执行 `sql/init.sql` 和 `sql/print_table.sql`。
3. 按环境修改 `src/main/resources/application-dev.yml` 或 `src/main/resources/application-prod.yml` 中的数据库账号和 LibreOffice 命令。
4. 启动后端，默认使用 `dev` 环境，端口为 `8081`：

```powershell
cd D:\work\shoe-factory-assistant\backend
$env:JAVA_HOME="D:\develop\JAVA\jdk-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

如需启动 `prod` 环境：

```powershell
cd D:\work\shoe-factory-assistant\backend
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

IDEA 里直接启动 `ShoeFactoryAssistantApplication` 时，请在 Spring Boot Run Configuration 的 Active profiles 填 `dev` 或 `prod`。如果没有 Active profiles 输入框，就在 Program arguments 填 `--spring.profiles.active=dev` 或 `--spring.profiles.active=prod`。

如果 `soffice` 不在系统 PATH 中，把 `app.file-storage.libre-office-command` 改成完整路径，例如：

```yaml
app:
  file-storage:
    libre-office-command: C:/Program Files/LibreOffice/program/soffice.exe
```
