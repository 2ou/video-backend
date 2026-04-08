from pydantic import BaseModel


class CanvasData(BaseModel):
    project_id: int
    version_id: int
    version_no: int
    canvas_json: dict
