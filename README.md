# 专业视频 AI 画布平台（Phase-1 MVP）

本仓库是本地可运行的 monorepo MVP：
- 后端：FastAPI + MySQL + Redis + Celery
- 前端：Vue3 + TypeScript + Vue Flow + Element Plus
- 存储：本地 uploads（已预留 OSS 抽象）

## 项目目录
- `backend/` 后端服务
- `frontend/` 前端应用
- `uploads/` 本地文件目录（raw/temp/result/cover）

## 本地依赖
- MySQL（本地）
- Redis（本地）

## 后端启动说明
1. 创建虚拟环境
   ```bash
   cd backend
   python -m venv .venv
   source .venv/bin/activate
   ```
2. 安装 requirements
   ```bash
   pip install -r requirements.txt
   ```
3. 配置 .env
   ```bash
   cp .env.example .env
   ```
4. 执行 Alembic migration
   ```bash
   alembic upgrade head
   ```
5. 启动 FastAPI
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```
6. 启动 Celery worker
   ```bash
   celery -A app.workers.celery_app.celery_app worker --loglevel=info
   ```

## 前端启动说明
1. 安装依赖
   ```bash
   cd frontend
   npm install
   ```
2. 启动开发服务
   ```bash
   npm run dev
   ```

## 启动命令示例
- 后端：`uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`
- Celery：`celery -A app.workers.celery_app.celery_app worker --loglevel=info`
- 前端：`npm run dev`
