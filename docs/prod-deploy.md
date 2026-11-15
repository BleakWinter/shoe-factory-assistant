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

旧 Debian 的 Jenkins 任务采用 `shoe-factory-<组件>-<动作>` 命名。后端构建任务 `shoe-factory-backend-build` 的流水线脚本对应仓库根目录的 `Jenkinsfile`。在 Pipeline 配置中选择 Pipeline script from SCM、Git 和要构建的分支。构建节点需要能以 Jenkins 服务账户运行 rootless Podman，并已导入 Java 17 Maven 镜像，标签为 `localhost/shoe-ci-maven:java17`。点 Build Now 会拉取该分支的代码，运行 Maven 测试并归档 JAR。构建工作区里开发和生产配置的数据库连接会替换成测试占位值。

## 旧 Debian 的 Jenkins 前端发布

`shoe-factory-frontend-deploy` 从 GitHub `main` 拉取代码，使用 Jenkins 账户的 rootless Podman 和 `localhost/shoe-ci-node:node24` 执行 `npm ci`、`npm run build`，再归档 `web/dist`。在 **Build with Parameters** 里勾选 `DEPLOY_FRONTEND` 才会更新网页。流水线脚本路径为 `deploy/Jenkinsfile.frontend`。

旧机 Nginx 以只读方式挂载 `/srv/shoe-factory-frontend`，从其中的 `current` 目录链接提供网页。发布脚本 `deploy/publish-frontend.sh` 写入新的版本目录，切换 `current`，并用网页实际返回的 `index.html` 验证发布。验证失败会恢复到前一个版本。上一版的 `/assets/` 文件可继续通过 Nginx 访问。

## Jenkins Docker 发布草案

在 Mac 上试跑 Jenkins LTS 时可使用 8082 端口，后端使用 8080：

```bash
brew install jenkins-lts
brew_root="$(brew --prefix)"
export PATH="/usr/local/bin:$brew_root/bin:$PATH"
"$brew_root/opt/openjdk@21/bin/java" -jar "$brew_root/opt/jenkins-lts/libexec/jenkins.war" \
  --httpListenAddress=127.0.0.1 --httpPort=8082
```

Docker 发布使用独立的 Pipeline 作业，脚本路径填 `deploy/Jenkinsfile.docker`。目标 Docker 所在节点使用 `shoe-factory-docker` 标签，在该节点的环境变量中设置 `DEPLOY_ROOT` 和 `BACKEND_ENV_FILE`。需要 Maven 镜像站的节点再设置 `MAVEN_SETTINGS_FILE=deploy/maven-public-settings.xml`。确认节点能执行 `docker version` 和 `docker compose version`。第一次先走默认的仅构建流程，确认构建后再勾选 `DEPLOY_BACKEND`。流水线使用 Git 提交号和构建号标记镜像，通过健康检查确认后端已能查询数据库。

如果将来迁移到 Debian 的 x86 旧电脑，构建目标应为 `linux/amd64`。Jenkins 可以留在 Mac 或其他构建主机，旧电脑通过镜像仓库拉取镜像，再在目标机执行 Compose 发布。

## Nacos 配置中心

在旧 Debian 上用 `deploy/compose.nacos.yml` 启动单机配置中心。把 `NACOS_SERVER_ENV_FILE` 指向仓库外的环境文件，文件权限设为 `600`。文件需要 `NACOS_AUTH_TOKEN`（用 `openssl rand -base64 48` 生成）、`NACOS_AUTH_IDENTITY_KEY` 和 `NACOS_AUTH_IDENTITY_VALUE`。Compose 将配置数据保存在 `shoe-factory-config_data` 卷中。新实例需要初始化管理员密码。

控制台通过旧机的 Tailscale Nginx 入口 `http://100.107.45.39/nacos/` 访问。Nginx 加入 `shoe-factory-config_default` 外部 Docker 网络，将 `/nacos/` 转发给 `nacos:8080`，并保留请求 URI 中的 `/nacos/` 前缀。客户端使用 Docker 内网的 `nacos:8848` 和 `9848`。

在 Nacos 中为测试和正式环境分别使用独立的命名空间。创建 YAML 类型的 `shoe-factory-backend.yml`，Group 填 `SHOE_FACTORY`。配置中集中管理 `spring.datasource` 的 URL、用户名、密码、`app.file-storage.root-path` 及需要的 `app.delivery-report` 和 `spring.mail`。为后端创建只读账号，并授予对应配置的读取权限。

启用配置中心时，后端需要 `SPRING_PROFILES_ACTIVE=prod,nacos` 和 `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD`。如果使用本机 Compose，将后端加入 Nacos 的 `shoe-factory-config_default` Docker 网络，连接地址填 `nacos:8848`。填写相应的命名空间 ID。通过 Nacos 管理的字段应从后端环境文件和 Compose 的 `environment` 移除，避免环境变量覆盖。文件存储路径要和容器内的数据卷挂载位置一致。修改配置后重启后端生效。

## 原虚拟机部署

目标虚拟机：`192.168.10.62`

## 1. 部署内容

后端上传 Spring Boot jar 到虚拟机，并用 Java 17 Docker 容器运行。

前端执行本机构建后上传静态文件到虚拟机：

```text
/data/web/shoe-factory-assistant
```

Nginx 负责访问前端页面，并把 `/api` 代理到后端 `8080`。

MySQL 使用虚拟机上已有数据库服务，不由部署脚本创建或管理。

### 多客户功能升级注意

部署多客户手工录单版本前，必须先备份目标业务数据库并执行 `sql/add_multi_customer_ledger.sql`，再部署后端与前端。部署脚本不会自动执行数据库迁移。新库先执行 `sql/print_table.sql`，再执行升级脚本。操作和数据口径见 [多客户录单与对账](多客户录单与对账.md)。

## 2. 虚拟机依赖

虚拟机需要安装：

```bash
docker
```

检查：

```bash
docker --version
```

## 3. 后端部署

在 Windows 本机项目根目录执行：

```powershell
.\deploy\deploy-backend.ps1
```

Git Bash 里执行：

```bash
./deploy/deploy-backend.sh
```

脚本会执行：

```text
1. mvn -Pprod -Dmaven.test.skip=true package
2. 上传 jar 到 /data/backend/shoe-factory-assistant/app.jar
3. 拉取 eclipse-temurin:17-jre-jammy
4. 如果容器不存在则创建，已存在则只重启
```

后端地址：

```text
http://192.168.10.62:8080
```

查看日志：

```bash
docker logs -f shoe-factory-backend
tail -f /data/backend/shoe-factory-assistant/logs/backend.log
tail -f /data/backend/shoe-factory-assistant/logs/backend-error.log
```

重启：

```bash
docker restart shoe-factory-backend
```

## 4. 前端部署

在 Windows 本机项目根目录执行：

```powershell
.\deploy\deploy-frontend.ps1
```

Git Bash 里执行：

```bash
./deploy/deploy-frontend.sh
```

脚本会执行：

```text
1. npm run build
2. 上传 web/dist 到 /data/web/shoe-factory-assistant
3. 修复文件权限
4. 重载或重启 Nginx
```

访问：

```text
http://192.168.10.62/
```

## 5. 数据库配置

后端使用 `backend/src/main/resources/application-prod.yml`：

```text
jdbc:mysql://192.168.10.62:3306/shoe_factory_assistant_prod
```

如果数据库账号密码变化，修改 `application-prod.yml` 后重新部署后端。

## 6. 文件位置

上传文件、图片和 PDF 保存在虚拟机：

```text
/data/shoe-factory-assistant/files
```

应用会按下面结构保存：

```text
/data/shoe-factory-assistant/files/年份/月/original
/data/shoe-factory-assistant/files/年份/月/images
/data/shoe-factory-assistant/files/年份/月/pdf
```
