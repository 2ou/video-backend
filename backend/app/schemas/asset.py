from datetime import datetime

from pydantic import BaseModel


class AssetData(BaseModel):
    id: int
    file_name: str
    file_type: str
    mime_type: str
    file_size: int
    file_url: str
    created_at: datetime

    class Config:
        from_attributes = True
