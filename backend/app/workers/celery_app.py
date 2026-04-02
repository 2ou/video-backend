from celery import Celery

from app.core.config import settings

celery_app = Celery(
    "ai_video_canvas",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.workers.run_tasks", "app.workers.polling_tasks"],
)

celery_app.conf.update(task_track_started=True, timezone="UTC")
