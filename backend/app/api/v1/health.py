from fastapi import APIRouter
from sqlalchemy import text

from app.core.database import SessionLocal
from app.core.redis import ping_redis
from app.schemas.common import BaseResponse, HealthData

router = APIRouter(tags=["health"])


@router.get("/health", response_model=BaseResponse[HealthData])
def health_check():
    mysql_status = "down"
    db = SessionLocal()
    try:
        db.execute(text("SELECT 1"))
        mysql_status = "up"
    except Exception:
        mysql_status = "down"
    finally:
        db.close()

    redis_status = "up" if ping_redis() else "down"
    return BaseResponse(data=HealthData(app="up", mysql=mysql_status, redis=redis_status))
