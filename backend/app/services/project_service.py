from app.core.exceptions import ForbiddenError, NotFoundError
from app.models.project import Project
from sqlalchemy.orm import Session


class ProjectService:
    @staticmethod
    def create(db: Session, user_id: int, name: str, description: str) -> Project:
        project = Project(user_id=user_id, name=name, description=description)
        db.add(project)
        db.commit()
        db.refresh(project)
        return project

    @staticmethod
    def list_by_user(db: Session, user_id: int) -> list[Project]:
        return db.query(Project).filter(Project.user_id == user_id).order_by(Project.id.desc()).all()

    @staticmethod
    def get(db: Session, user_id: int, project_id: int) -> Project:
        project = db.get(Project, project_id)
        if not project:
            raise NotFoundError("项目不存在")
        if project.user_id != user_id:
            raise ForbiddenError("无权限访问该项目")
        return project

    @staticmethod
    def update(db: Session, project: Project, name: str, description: str, cover_url: str) -> Project:
        project.name = name
        project.description = description
        project.cover_url = cover_url
        db.commit()
        db.refresh(project)
        return project
