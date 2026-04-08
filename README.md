# 专业视频 AI 画布平台（Phase-1 MVP）

Monorepo：
- backend: Java Spring Boot
- frontend: Vue3 + TS + Vue Flow
- uploads: 本地文件存储

## 本地依赖
- MySQL
- Redis
- Java 17
- Node.js 18+

## 后端启动
```bash
cd backend
cp .env.example .env
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

## 前端启动
```bash
cd frontend
npm install
npm run dev
```

## 启动命令示例
- 后端：`mvn spring-boot:run`
- 前端：`npm run dev`
