from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.deps.auth import get_current_user
from app.models.user import User
from app.schemas.common import BaseResponse
from app.schemas.run import RunCreateResponse, RunDetailData, RunNodeData
from app.services.project_service import ProjectService
from app.services.run_service import RunService
from app.services.workflow_service import WorkflowService

router = APIRouter(tags=["runs"])


@router.post("/api/v1/projects/{project_id}/run", response_model=BaseResponse[RunCreateResponse])
def run_project(project_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    project = ProjectService.get(db, user.id, project_id)
    run = WorkflowService.create_and_run(db, user.id, project)
    return BaseResponse(data=RunCreateResponse(run_id=run.id, status=run.status))


@router.get("/api/v1/runs/{run_id}", response_model=BaseResponse[RunDetailData])
def get_run(run_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    run = RunService.get_run(db, run_id, user.id)
    return BaseResponse(
        data=RunDetailData(
            id=run.id,
            project_id=run.project_id,
            version_id=run.version_id,
            status=run.status,
            input_json=run.input_json,
            output_json=run.output_json,
            error_message=run.error_message,
            started_at=run.started_at,
            finished_at=run.finished_at,
            created_at=run.created_at,
            is_finished=RunService.is_finished(run),
        )
    )


@router.get("/api/v1/runs/{run_id}/nodes", response_model=BaseResponse[list[RunNodeData]])
def get_run_nodes(run_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    _ = RunService.get_run(db, run_id, user.id)
    nodes = RunService.get_nodes(db, run_id)
    return BaseResponse(data=[RunNodeData.model_validate(n) for n in nodes])
