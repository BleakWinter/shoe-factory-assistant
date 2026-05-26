# 生产部署

目标虚拟机：`192.168.10.62`

## 1. 部署内容

后端上传 Spring Boot jar 到虚拟机，并用 Java 17 Docker 容器运行。

前端执行本机构建后上传静态文件到虚拟机：

```text
/data/web/shoe-factory-assistant
```

Nginx 负责访问前端页面，并把 `/api` 代理到后端 `8080`。

MySQL 使用虚拟机上已有数据库服务，不由部署脚本创建或管理。

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
