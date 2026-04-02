from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from sqlalchemy import select

from app.api.v1 import assets, auth, canvas, health, projects, runs
from app.core.database import Base, SessionLocal, engine
from app.core.exceptions import AppError, app_exception_handler, unhandled_exception_handler
from app.core.logger import setup_logging
from app.core.security import hash_password
from app.models.user import User

setup_logging()
app = FastAPI(title="AI Video Canvas Backend")

app.include_router(auth.router)
app.include_router(projects.router)
app.include_router(canvas.router)
app.include_router(assets.router)
app.include_router(runs.router)
app.include_router(health.router)

app.add_exception_handler(AppError, app_exception_handler)
app.add_exception_handler(Exception, unhandled_exception_handler)


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


@app.on_event("startup")
def on_startup() -> None:
    Base.metadata.create_all(bind=engine)
    init_default_user()


uploads_path = Path("uploads")
uploads_path.mkdir(exist_ok=True)
app.mount("/files", StaticFiles(directory=str(uploads_path)), name="files")
