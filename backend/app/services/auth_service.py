from sqlalchemy.orm import Session

from app.core.exceptions import UnauthorizedError
from app.core.security import create_access_token, verify_password
from app.models.user import User


class AuthService:
    @staticmethod
    def login(db: Session, username: str, password: str) -> str:
        user = db.query(User).filter(User.username == username).first()
        if not user or not verify_password(password, user.password_hash):
            raise UnauthorizedError("用户名或密码错误")
        return create_access_token(str(user.id))
