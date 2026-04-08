# AI Video Canvas Backend (Java)

## 技术栈
- Java 17
- Spring Boot 3
- Spring Data JPA (MySQL)
- Spring Data Redis
- JWT (jjwt)
- 异步任务 + 定时调度（替代 Celery）

## 启动步骤
1. 配置环境变量
   ```bash
   cp .env.example .env
   export $(grep -v '^#' .env | xargs)
   ```
2. 启动后端
   ```bash
   mvn spring-boot:run
   ```

## 默认账号
- admin / 123456

## 主要接口
- POST /api/v1/auth/login
- GET /api/v1/auth/me
- POST /api/v1/projects
- GET /api/v1/projects
- GET /api/v1/projects/{project_id}
- PUT /api/v1/projects/{project_id}
- GET /api/v1/projects/{project_id}/canvas
- PUT /api/v1/projects/{project_id}/canvas
- POST /api/v1/assets/upload
- POST /api/v1/projects/{project_id}/run
- GET /api/v1/runs/{run_id}
- GET /api/v1/runs/{run_id}/nodes
- GET /health
