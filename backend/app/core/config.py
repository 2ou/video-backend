from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "ai-video-canvas-backend"
    app_env: str = "dev"
    app_host: str = "0.0.0.0"
    app_port: int = 8000

    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "123456"
    mysql_db: str = "ai_video_canvas"

    redis_host: str = "127.0.0.1"
    redis_port: int = 6379
    redis_db: int = 0

    jwt_secret: str = "replace_me"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 10080

    upload_dir: str = "./uploads"
    public_base_url: str = "http://127.0.0.1:8000"

    kie_api_key: str = "replace_me"
    kie_base_url: str = "https://api.kie.ai"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @property
    def database_url(self) -> str:
        return (
            f"mysql+pymysql://{self.mysql_user}:{self.mysql_password}@"
            f"{self.mysql_host}:{self.mysql_port}/{self.mysql_db}?charset=utf8mb4"
        )

    @property
    def redis_url(self) -> str:
        return f"redis://{self.redis_host}:{self.redis_port}/{self.redis_db}"

    def ensure_upload_dirs(self) -> None:
        for sub in ["raw", "temp", "result", "cover"]:
            Path(self.upload_dir, sub).mkdir(parents=True, exist_ok=True)


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.ensure_upload_dirs()
    return settings


settings = get_settings()
