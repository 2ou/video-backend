from pathlib import Path
from contextlib import asynccontextmanager

import uvicorn

from app.api.v1 import assets, auth, canvas, health, projects, runs
from app.core.database import Base, SessionLocal, engine
from app.core.exceptions import AppError, app_exception_handler, unhandled_exception_handler
from app.core.logger import setup_logging
from app.core.security import hash_password
from app.models.user import User
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from sqlalchemy import select

setup_logging()


def init_default_user() -> None:
    db = SessionLocal()
    try:
        user = db.execute(select(User).where(User.username == "admin")).scalar_one_or_none()
        if not user:
            db.add(
                User(
                    username="admin",
                    password_hash=hash_password("123456"),
                    nickname="Administrator",
                    role="admin",
                )
            )
            db.commit()
    finally:
        db.close()



@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    init_default_user()
    yield


app = FastAPI(title="AI Video Canvas Backend", lifespan=lifespan)

app.include_router(auth.router)
app.include_router(projects.router)
app.include_router(canvas.router)
app.include_router(assets.router)
app.include_router(runs.router)
app.include_router(health.router)

app.add_exception_handler(AppError, app_exception_handler)
app.add_exception_handler(Exception, unhandled_exception_handler)


uploads_path = Path("uploads")
uploads_path.mkdir(exist_ok=True)
app.mount("/files", StaticFiles(directory=str(uploads_path)), name="files")

# 将下面这段加在 main.py 的最底部
if __name__ == "__main__":
    # 启动 uvicorn 服务器，host 绑定本地，端口 8000
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True)