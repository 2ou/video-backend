# AI Video Canvas Backend

## 1. 环境准备
- Python 3.11+
- 本地 MySQL
- 本地 Redis

## 2. 启动步骤
1. 创建虚拟环境
   ```bash
   python -m venv .venv
   source .venv/bin/activate
   ```
2. 安装依赖
   ```bash
   pip install -r requirements.txt
   ```
3. 配置环境变量
   ```bash
   cp .env.example .env
   ```
4. 执行数据库迁移
   ```bash
   alembic upgrade head
   ```
5. 启动 FastAPI
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```
6. 启动 Celery Worker
   ```bash
   celery -A app.workers.celery_app.celery_app worker --loglevel=info
   ```

## 3. 默认账号
- username: `admin`
- password: `123456`

## 4. 关键接口
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/projects`
- `GET /api/v1/projects`
- `GET /api/v1/projects/{project_id}/canvas`
- `PUT /api/v1/projects/{project_id}/canvas`
- `POST /api/v1/assets/upload`
- `POST /api/v1/projects/{project_id}/run`
- `GET /api/v1/runs/{run_id}`
- `GET /api/v1/runs/{run_id}/nodes`
- `GET /health`
