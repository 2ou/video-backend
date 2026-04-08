from typing import Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class BaseResponse(BaseModel, Generic[T]):
    code: str = "OK"
    message: str = "success"
    data: T | None = None


class IdResponse(BaseModel):
    id: int


class HealthData(BaseModel):
    app: str
    mysql: str
    redis: str
