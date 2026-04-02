from fastapi import APIRouter, Depends, File, Form, UploadFile
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.deps.auth import get_current_user
from app.models.user import User
from app.schemas.asset import AssetData
from app.schemas.common import BaseResponse
from app.services.asset_service import AssetService

router = APIRouter(prefix="/api/v1/assets", tags=["assets"])


@router.post("/upload", response_model=BaseResponse[AssetData])
def upload_asset(
    file: UploadFile = File(...),
    project_id: int | None = Form(default=None),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    asset = AssetService.upload(db, user.id, project_id, file)
    return BaseResponse(data=AssetData.model_validate(asset))
