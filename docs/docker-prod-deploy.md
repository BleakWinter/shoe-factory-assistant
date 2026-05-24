# Docker 生产部署

目标虚拟机：`192.168.245.130`

## 1. 部署内容

这套部署只用 Docker Compose 管理后端。前端由虚拟机上已有的 Nginx 容器托管静态文件。

```text
backend  Spring Boot 后端，prod 环境，暴露 8080
nginx    已有 Nginx 容器，暴露 80，读取 /data/web/shoe-factory-assistant
```

MySQL 使用虚拟机上已有的数据库服务，不由这个 Compose 创建或管理。

部署文件统一放在 `deploy` 目录：

```text
deploy/
  docker-compose.prod.yml
  deploy-frontend.ps1
  backend/
    Dockerfile
    Dockerfile.dockerignore
```

## 2. 安装 Docker

在虚拟机上安装 Docker 和 Docker Compose 插件。

## 3. 上传项目

把整个项目目录上传到虚拟机，例如：

```bash
/opt/shoe-factory-assistant
```

## 4. 启动生产环境

启动或更新后端：

```bash
cd /opt/shoe-factory-assistant
docker compose -f deploy/docker-compose.prod.yml up -d --build
```

发布前端：

```powershell
.\deploy\deploy-frontend.ps1
```

启动后访问：

```text
http://192.168.245.130/
```

## 5. 数据库配置

后端使用 `backend/src/main/resources/application-prod.yml` 里的数据库配置：

```text
jdbc:mysql://192.168.245.130:3306/shoe_factory_assistant_prod
```

如果数据库账号密码变化，修改 `application-prod.yml` 后重新部署。

## 6. 文件位置

上传文件、图片和 PDF 保存在宿主机：

```text
/opt/shoe-factory-assistant/data/files
```

容器内路径：

```text
/data/shoe-factory-assistant/files
```

应用会按下面结构保存：

```text
/data/shoe-factory-assistant/files/年份/月/original
/data/shoe-factory-assistant/files/年份/月/images
/data/shoe-factory-assistant/files/年份/月/pdf
```

## 7. 常用命令

查看状态：

```bash
docker compose -f deploy/docker-compose.prod.yml ps
```

查看日志：

```bash
docker compose -f deploy/docker-compose.prod.yml logs -f backend
docker logs -f nginx
```

重启：

```bash
docker compose -f deploy/docker-compose.prod.yml restart
```

更新代码后重新部署：

```bash
docker compose -f deploy/docker-compose.prod.yml up -d --build
```
