# 生产部署

## 本机 Docker Compose 发布

本机 Compose 项目名为 `shoe-factory`，管理 Java 后端 `backend`、Nginx 前端 `frontend` 和 Python `data-service`，各自运行在独立容器里。MySQL 独立运行，沿用现有数据库连接。Compose 文件沿用原路径 `deploy/compose.backend.yml`。

后端镜像在 Docker 中打包 Java 17 应用并运行 Maven 测试，运行镜像包含 LibreOffice Calc 和中文字体，用于订单、装箱单的 PDF 预览。

首次切换时，先按当前 Docker 容器的配置准备宿主机部署目录和环境变量文件。Mac 上现有部署目录是 `/Volumes/T9/Develop/shoe-factory-assistant`。沿用该目录中的 `backend/logs` 和 `files`，以及 Python 服务使用的 `shoe-factory-data` Docker 网络。把以下环境变量按 `KEY=value` 格式写入仓库外的 `backend/backend.env`，文件权限设为 `600`，直接填写值。Compose 2.30 或更高版本会按原样读取值，保留密码里的 `$` 等字符。

- 数据库使用 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`，沿用当前后端的值。
- 日报沿用现有 `delivery-report.env` 里的配置项，包括 `APP_DELIVERY_REPORT_ENABLED`、`SPRING_MAIL_USERNAME`、`SPRING_MAIL_PASSWORD` 和 `APP_DELIVERY_REPORT_TRIGGER_TOKEN`。

从仓库根目录执行：

```bash
export DEPLOY_ROOT=/Volumes/T9/Develop/shoe-factory-assistant
export BACKEND_ENV_FILE="$DEPLOY_ROOT/backend/backend.env"
export BACKEND_IMAGE=shoe-factory-backend:jenkins-check
docker compose -f deploy/compose.backend.yml ps
docker compose -f deploy/compose.backend.yml config --quiet
```

发布新后端时构建并指定新镜像：

```bash
export BACKEND_IMAGE="shoe-factory-backend:$(git rev-parse --short=12 HEAD)-local"
docker build --secret id=maven_settings,src=deploy/maven-public-settings.xml -t "$BACKEND_IMAGE" backend
```

Mac 的 Docker 构建环境使用仓库内只含公开镜像地址的 Maven 设置。可以直接访问 Maven Central 的构建节点可省略 `--secret`。

如果当前后端由 `docker run` 创建，镜像构建成功后，第一次切换需停止并重命名现有的 `shoe-factory-backend`，腾出容器名和 8080 端口。保留该容器作为回退版本。然后执行以下命令，后续发布也使用同一条：

```bash
docker compose -f deploy/compose.backend.yml up -d --no-deps --no-build --wait --wait-timeout 150 backend
```

发布后打开订单和装箱单各做一次 PDF 预览。第一次切换失败时，用 `docker compose -f deploy/compose.backend.yml rm -sf backend` 移除这次创建的后端，把旧容器改回原名并启动。由 Compose 接管后的发布可指定上一次的 `BACKEND_IMAGE` 再执行发布命令回退。

前端沿用宿主机上已有的 Nginx 配置、静态文件和日志，目录为 `$DEPLOY_ROOT/frontend`。更新网页文件后重启前端：

```bash
docker compose -f deploy/compose.backend.yml restart frontend
```

Python 发布时构建新镜像，再单独更新 `data-service`：

```bash
export DATA_SERVICE_IMAGE="shoe-factory-data-service:$(git rev-parse --short=12 HEAD)-local"
docker build -t "$DATA_SERVICE_IMAGE" data-service
docker compose -f deploy/compose.backend.yml up -d --no-deps --pull never --wait data-service
```

## Jenkins 后端构建

homeserver 的 Jenkins 任务采用 `shoe-factory-<组件>-<动作>` 命名。`shoe-factory-backend-build` 对应仓库根目录的 `Jenkinsfile`。点 **Build Now** 会拉取 GitHub `main`，在 rootless Podman 中运行 Maven 测试，归档 JAR，并在成功后自动发布到 homeserver 的生产环境。构建工作区里开发和生产配置的数据库连接会替换成测试占位值。

`shoe-factory-backend-release.path` 监视 Jenkins 的发布请求目录。对应的 systemd 服务运行 `deploy/release-backend.py`，核对 JAR 的 SHA-256，再用带 Java 17、LibreOffice 和中文字体的运行镜像打包。`deploy/compose.backend.debian.yml` 从仓库外的 `/srv/shoe-factory-backend/nacos.env` 读取 Nacos 凭据。后端以非 root 账户运行，宿主机 `/srv/shoe-factory-backend/files-prod` 挂载到容器的 `/data/shoe-factory-assistant/files`。

发布时先在 Docker 内网检查新容器的 `/api/customer-configs`，再核对 `shoe_factory_app` 连接 `shoe_factory_assistant_prod` 的实际 TLS 会话。通过后切换并重载 Nginx 的 `/api` 上游。若新上游验证失败，恢复上一次的 Nginx 配置。开发环境在 Mac 运行。

## 旧 Debian 的 Jenkins 前端发布

`shoe-factory-frontend-deploy` 从 GitHub `main` 拉取代码，使用 Jenkins 账户的 rootless Podman 和 `localhost/shoe-ci-node:node24` 执行 `npm ci`、`npm run build`，再归档 `web/dist`。在 **Build with Parameters** 里勾选 `DEPLOY_FRONTEND` 才会更新网页。仓库版本为 `deploy/Jenkinsfile.frontend`，运行版本保存在 Jenkins 任务的 Pipeline 配置中。

旧机 Nginx 以只读方式挂载 `/srv/shoe-factory-frontend`，从其中的 `current` 目录链接提供网页。发布脚本 `deploy/publish-frontend.sh` 写入新的版本目录，切换 `current`，并用网页实际返回的 `index.html` 验证发布。验证失败会恢复到前一个版本。上一版的 `/assets/` 文件可继续通过 Nginx 访问。

验证成功后只保留 `current`、`previous` 指向的发布版本，清除本任务生成的更早目录。回退成功后移除失败版。构建结束清理工作区，归档保留最近 3 份，npm 内容缓存超过 512 MiB 时清空。

## homeserver 的 Jenkins 数据服务发布

`shoe-factory-data-service-deploy` 对应 `deploy/Jenkinsfile.data-service`。点 **Build Now** 后从 GitHub `main` 拉取代码，在 Jenkins 的 rootless Podman 中构建 Python 镜像，运行现有渲染测试，成功后把带 Git 提交号和构建号的镜像交给 systemd 发布。构建机需预载 `docker.io/library/python:3.12-slim-bookworm`，并能访问 Debian 软件源与 PyPI。

发布服务校验镜像归档及 SHA-256，在 `shoe-factory-config_default` 内网用样例请求生成 PNG。候选通过后才切换 `data-service` 容器，切换后再次检查 `http://data-service:8000/reports/delivery/render`。失败会恢复原容器。Java 可继续用同一地址渲染日报。

Docker 保留运行镜像和 `localhost/shoe-factory-data-service:previous` 作为回滚版本。发布过程中的镜像包、验证容器和工作区会清理，Jenkins 保留最近 10 次构建记录，容器日志按三份 5 MiB 轮转。

首次安装发布监听时，以 root 在 homeserver 执行以下命令，再用同一 GitHub 只读凭据建立 Jenkins Pipeline：

```bash
queue=/srv/shoe-factory-data-service-queue
install -d -o root -g jenkins -m 0750 "$queue"
install -d -o jenkins -g jenkins -m 0700 "$queue/requests"
install -d -o root -g root -m 0700 "$queue/processing"
install -d -o root -g jenkins -m 2750 "$queue/results"
install -m 0755 deploy/release-data-service.py /usr/local/sbin/shoe-factory-data-service-release
install -m 0644 deploy/shoe-factory-data-service-release.{path,service} /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now shoe-factory-data-service-release.path
```

## homeserver 的 MySQL

`deploy/compose.mysql.yml` 独立运行 MySQL 8.4 LTS，数据保存在 `shoe-factory-mysql_mysql-data` 卷。首次部署使用两份仓库外的密码文件，权限设为 `600`，通过 `MYSQL_ROOT_PASSWORD_PATH` 和 `MYSQL_APP_PASSWORD_PATH` 指定，并用 `MYSQL_DATABASE` 指定初始化库。管理员 `root` 在容器内使用。

首次启动前创建 Docker 网络 `shoe-factory-data`。应用容器加入该网络后使用 `mysql:3306` 连接。生产连接信息保存在 Nacos 的 `shoe-factory-prod` 命名空间，连接启用 TLS。`shoe_factory_app` 对 `shoe_factory_assistant_prod` 有 `SELECT`、`INSERT`、`UPDATE`、`DELETE` 权限。恢复业务数据前，先用现用 MySQL 的逻辑备份演练导入和应用验收。

## Nacos 配置中心

在旧 Debian 上用 `deploy/compose.nacos.yml` 启动单机配置中心。把 `NACOS_SERVER_ENV_FILE` 指向仓库外的环境文件，文件权限设为 `600`。文件需要 `NACOS_AUTH_TOKEN`（用 `openssl rand -base64 48` 生成）、`NACOS_AUTH_IDENTITY_KEY` 和 `NACOS_AUTH_IDENTITY_VALUE`。Compose 将配置数据保存在 `shoe-factory-config_data` 卷中。新实例需要初始化管理员密码。

控制台通过旧机的 Tailscale Nginx 入口 `http://100.107.45.39/nacos/` 访问。Nginx 加入 `shoe-factory-config_default` 外部 Docker 网络，将 `/nacos/` 转发给 `nacos:8080`，并保留请求 URI 中的 `/nacos/` 前缀。客户端使用 Docker 内网的 `nacos:8848` 和 `9848`。

在 Nacos 中为测试和正式环境分别使用独立的命名空间。创建 YAML 类型的 `shoe-factory-backend.yml`，Group 填 `SHOE_FACTORY`。配置中集中管理 `spring.datasource` 的 URL、用户名、密码、`app.file-storage.root-path` 及需要的 `app.delivery-report` 和 `spring.mail`。为后端创建只读账号，并授予对应配置的读取权限。

启用配置中心时，后端需要 `SPRING_PROFILES_ACTIVE=prod,nacos` 和 `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD`。如果使用本机 Compose，将后端加入 Nacos 的 `shoe-factory-config_default` Docker 网络，连接地址填 `nacos:8848`。填写相应的命名空间 ID。通过 Nacos 管理的字段应从后端环境文件和 Compose 的 `environment` 移除，避免环境变量覆盖。文件存储路径要和容器内的数据卷挂载位置一致。修改配置后重启后端生效。

homeserver 的预警配置在 `shoe-factory-prod` 命名空间的 `shoe-factory-backend.yml` 中，Group 为 `SHOE_FACTORY`。`app.delivery-report.enabled` 控制预警功能，`app.delivery-report.cron` 设为 `"0 30 8 * * *"` 时每天北京时间 08:30 自动发送，设为 `"-"` 时暂停定时发送。容器环境文件中的 `APP_DELIVERY_REPORT_ENABLED` 和 `APP_DELIVERY_REPORT_CRON` 应移除，首次迁移后重新创建容器。当前关闭 Nacos 动态刷新，后续修改配置并发布后重启后端生效。

## 多客户功能升级

部署多客户手工录单版本前，必须先备份目标业务数据库并执行 `sql/add_multi_customer_ledger.sql`，再部署后端与前端。数据库迁移需要手动执行。新库先执行 `sql/print_table.sql`，再执行升级脚本。操作和数据口径见 [多客户录单与对账](多客户录单与对账.md)。

## 业务文件归档

上传文件、图片和 PDF 使用容器内的 `/data/shoe-factory-assistant/files`。归档位置按年月和文件类型组织。

```text
/data/shoe-factory-assistant/files/年份/月/original
/data/shoe-factory-assistant/files/年份/月/images
/data/shoe-factory-assistant/files/年份/月/pdf
```
