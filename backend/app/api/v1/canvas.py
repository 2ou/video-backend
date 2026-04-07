from app.core.database import get_db
from app.deps.auth import get_current_user
from app.models.user import User
from app.schemas.canvas import CanvasData
from app.schemas.common import BaseResponse
from app.services.canvas_service import CanvasService
from app.services.project_service import ProjectService
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session

router = APIRouter(prefix="/api/v1/projects", tags=["canvas"])


class CanvasSaveRequest(BaseModel):
    canvas_json: dict
    remark: str = "save"


@router.get("/{project_id}/canvas", response_model=BaseResponse[CanvasData])
def get_canvas(project_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    project = ProjectService.get(db, user.id, project_id)
    version = CanvasService.get_latest_or_create(db, project, user.id)
    return BaseResponse(
        data=CanvasData(
            project_id=project_id,
            version_id=version.id,
            version_no=version.version_no,
            canvas_json=version.canvas_json,
        )
    )


@router.put("/{project_id}/canvas", response_model=BaseResponse[CanvasData])
def save_canvas(
    project_id: int,
    payload: CanvasSaveRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    project = ProjectService.get(db, user.id, project_id)
    version = CanvasService.save_new_version(db, project, user.id, payload.canvas_json, payload.remark)
    return BaseResponse(
        data=CanvasData(
            project_id=project_id,
            version_id=version.id,
            version_no=version.version_no,
            canvas_json=version.canvas_json,
        )
    )
