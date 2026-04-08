from pydantic import BaseModel


class LoginRequest(BaseModel):
    username: str
    password: str


class LoginData(BaseModel):
    access_token: str
    token_type: str = "bearer"


class MeData(BaseModel):
    id: int
    username: str
    nickname: str
    role: str
    status: str
