from app.core.database import get_db
from app.deps.auth import get_current_user
from app.models.user import User
from app.schemas.common import BaseResponse
from app.schemas.project import ProjectCreateRequest, ProjectData, ProjectUpdateRequest
from app.services.project_service import ProjectService
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

router = APIRouter(prefix="/api/v1/projects", tags=["projects"])


@router.post("", response_model=BaseResponse[ProjectData])
def create_project(payload: ProjectCreateRequest, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    project = ProjectService.create(db, user.id, payload.name, payload.description)
    return BaseResponse(data=ProjectData.model_validate(project))


@router.get("", response_model=BaseResponse[list[ProjectData]])
def list_projects(db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    projects = ProjectService.list_by_user(db, user.id)
    return BaseResponse(data=[ProjectData.model_validate(p) for p in projects])


@router.get("/{project_id}", response_model=BaseResponse[ProjectData])
def get_project(project_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    project = ProjectService.get(db, user.id, project_id)
    return BaseResponse(data=ProjectData.model_validate(project))


@router.put("/{project_id}", response_model=BaseResponse[ProjectData])
def update_project(
    project_id: int,
    payload: ProjectUpdateRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    project = ProjectService.get(db, user.id, project_id)
    project = ProjectService.update(db, project, payload.name, payload.description, payload.cover_url)
    return BaseResponse(data=ProjectData.model_validate(project))
