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

## 货期预警日报

启用前在目标库执行 [`sql/add_delivery_report_send.sql`](../sql/add_delivery_report_send.sql)，并确认后端能访问 `data-service`。Java 读取待出货明细，按客户、订单和交期分组，汇总到款号。邮件正文和微信群文案、PNG 都覆盖逾期、今日、未来 7 天到期的订单。图片交期分两行：上行日期，下行「逾期 N 天 / 今日到期 / 剩余 N 天」；按交期分档：逾期红底红字、剩余 3 天内橙底、剩余 4–5 天黄底并同步文字颜色、剩余 6–7 天不着色；订单组之间用深绿分割线隔开。图片通过内网返回字节，直接作为邮件附件。

给后端进程或容器配置这些环境变量：

| 环境变量 | 用途 |
| --- | --- |
| `APP_DELIVERY_REPORT_ENABLED=true` | 启用定时任务和手动接口，默认关闭 |
| `SPRING_MAIL_USERNAME` | QQ 发件账号 |
| `SPRING_MAIL_PASSWORD` | QQ SMTP 授权码 |
| `APP_DELIVERY_REPORT_TRIGGER_TOKEN` | 手动发送令牌 |
| `APP_DELIVERY_REPORT_RECIPIENT` | 可选收件地址，默认发送到发件账号 |
| `APP_DELIVERY_REPORT_RENDER_URL` | 可选绘图地址，默认 `http://data-service:8000/reports/delivery/render` |
| `APP_DELIVERY_REPORT_CRON` | 可选，默认每天北京时间 08:30，填 `-` 可只开手动接口 |

homeserver 使用 Nacos 的 `shoe-factory-prod` 命名空间、`SHOE_FACTORY` Group 和 `shoe-factory-backend.yml` 管理预警开关与发送时间。

```yaml
app:
  delivery-report:
    enabled: true
    cron: "0 30 8 * * *"
```

`enabled: false` 关闭预警功能，`cron: "-"` 暂停定时发送。当前 Nacos 导入使用 `refreshEnabled=false`，发布配置后重启后端生效。首次从环境变量迁移时，从容器环境文件移除 `APP_DELIVERY_REPORT_ENABLED` 和 `APP_DELIVERY_REPORT_CRON`，再重新创建容器，确保后端读取 Nacos 的值。

在首次创建或重建 Docker 容器时通过 `--env-file` 传入这些变量。把变量按 `KEY=value` 格式写入仓库外的环境变量文件，限制文件权限为 `600`。运行本机部署脚本时指定该文件的路径，沿用原容器的数据库和部署参数。

```bash
REPORT_ENV_FILE=/absolute/path/delivery-report.env bash deploy/deploy-backend-local-docker.sh
```

Java 容器需要与 Python 容器接到同一个自定义网络。新建 Java 容器时，如果 `shoe-factory-data` 网络已存在，脚本会自动接入。日常发布可直接运行脚本，重启容器会沿用创建时的环境变量。下次需要修改这些变量时，更新环境变量文件后重新创建容器。

手动试发：

```bash
curl --fail-with-body -X POST 'http://localhost:8080/api/reports/delivery/send?test=true' \
  -H "X-Report-Token: $APP_DELIVERY_REPORT_TRIGGER_TOKEN"
```

去掉 `test=true` 可手动发送当天正式日报。它与定时任务共用数据库发送记录，同一天已经发送时返回 `ALREADY_SENT`。试发主题带「试发」，不占用当天正式发送记录。结果 `SENT` 表示 SMTP 已接收。生成失败或 SMTP 认证失败可重试。若发送结果为 `UNKNOWN`，或中断后记录停留在 `SENDING`，先核对收件箱和后端日志。确认未发送后，可把该日期的记录改为 `FAILED`，再手动发一次。
