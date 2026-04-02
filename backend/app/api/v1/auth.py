from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.deps.auth import get_current_user
from app.models.user import User
from app.schemas.auth import LoginData, LoginRequest, MeData
from app.schemas.common import BaseResponse
from app.services.auth_service import AuthService

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


@router.post("/login", response_model=BaseResponse[LoginData])
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    token = AuthService.login(db, payload.username, payload.password)
    return BaseResponse(data=LoginData(access_token=token))


@router.get("/me", response_model=BaseResponse[MeData])
def me(current_user: User = Depends(get_current_user)):
    return BaseResponse(
        data=MeData(
            id=current_user.id,
            username=current_user.username,
            nickname=current_user.nickname,
            role=current_user.role,
            status=current_user.status,
        )
    )
