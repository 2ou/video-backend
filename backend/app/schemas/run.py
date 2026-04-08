from datetime import datetime

from pydantic import BaseModel


class RunCreateResponse(BaseModel):
    run_id: int
    status: str


class RunNodeData(BaseModel):
    id: int
    node_id: str
    node_type: str
    status: str
    provider: str
    provider_task_id: str
    input_json: dict
    output_json: dict
    error_message: str
    started_at: datetime | None
    finished_at: datetime | None

    class Config:
        from_attributes = True


class RunDetailData(BaseModel):
    id: int
    project_id: int
    version_id: int
    status: str
    input_json: dict
    output_json: dict
    error_message: str
    started_at: datetime | None
    finished_at: datetime | None
    created_at: datetime
    is_finished: bool

    class Config:
        from_attributes = True
