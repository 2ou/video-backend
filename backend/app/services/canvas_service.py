from sqlalchemy import func
from sqlalchemy.orm import Session

from app.models.project import Project
from app.models.project_version import ProjectVersion
from app.utils.default_canvas import DEFAULT_CANVAS_JSON


class CanvasService:
    @staticmethod
    def get_latest_or_create(db: Session, project: Project, user_id: int) -> ProjectVersion:
        version = None
        if project.latest_version_id:
            version = db.get(ProjectVersion, project.latest_version_id)
        if version:
            return version

        version = ProjectVersion(
            project_id=project.id,
            version_no=1,
            canvas_json=DEFAULT_CANVAS_JSON,
            remark="init",
            created_by=user_id,
        )
        db.add(version)
        db.flush()
        project.latest_version_id = version.id
        db.commit()
        db.refresh(version)
        return version

    @staticmethod
    def save_new_version(db: Session, project: Project, user_id: int, canvas_json: dict, remark: str = "save") -> ProjectVersion:
        latest_no = (
            db.query(func.max(ProjectVersion.version_no)).filter(ProjectVersion.project_id == project.id).scalar() or 0
        )
        version = ProjectVersion(
            project_id=project.id,
            version_no=latest_no + 1,
            canvas_json=canvas_json,
            remark=remark,
            created_by=user_id,
        )
        db.add(version)
        db.flush()
        project.latest_version_id = version.id
        db.commit()
        db.refresh(version)
        return version
