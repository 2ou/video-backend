from datetime import datetime

from pydantic import BaseModel


class ProjectCreateRequest(BaseModel):
    name: str
    description: str = ""


class ProjectUpdateRequest(BaseModel):
    name: str
    description: str = ""
    cover_url: str = ""


class ProjectData(BaseModel):
    id: int
    name: str
    description: str
    cover_url: str
    latest_version_id: int | None
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
