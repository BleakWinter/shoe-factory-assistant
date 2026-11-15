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

## Jenkins 后端发布

在 Mac 上试跑 Jenkins LTS 时可使用 8082 端口，后端使用 8080：

```bash
brew install jenkins-lts
brew_root="$(brew --prefix)"
export PATH="/usr/local/bin:$brew_root/bin:$PATH"
"$brew_root/opt/openjdk@21/bin/java" -jar "$brew_root/opt/jenkins-lts/libexec/jenkins.war" \
  --httpListenAddress=127.0.0.1 --httpPort=8082
```

在 Jenkins 新建一个 Pipeline 作业，选择 Pipeline script from SCM，填写 Git 仓库地址、Git 凭据和发布分支 `main`，脚本路径填 `Jenkinsfile`。目标 Docker 所在节点使用 `shoe-factory-docker` 标签，在该节点的环境变量中设置 `DEPLOY_ROOT` 和 `BACKEND_ENV_FILE`。需要 Maven 镜像站的节点再设置 `MAVEN_SETTINGS_FILE=deploy/maven-public-settings.xml`。确认节点能执行 `docker version` 和 `docker compose version`。第一次先走默认的仅构建流程，确认构建后再勾选 `DEPLOY_BACKEND`。流水线使用 Git 提交号和构建号标记镜像，通过健康检查确认后端已能查询数据库。

如果将来迁移到 Debian 的 x86 旧电脑，构建目标应为 `linux/amd64`。Jenkins 可以留在 Mac 或其他构建主机，旧电脑通过镜像仓库拉取镜像，再在目标机执行 Compose 发布。

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
