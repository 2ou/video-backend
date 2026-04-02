from fastapi import UploadFile
from sqlalchemy.orm import Session

from app.models.asset import Asset
from app.services.storage_service import storage_service


class AssetService:
    @staticmethod
    def upload(db: Session, user_id: int, project_id: int | None, file: UploadFile) -> Asset:
        saved = storage_service.save_upload(file, "raw")
        file_type = "video" if (file.content_type or "").startswith("video") else "image"
        asset = Asset(
            user_id=user_id,
            project_id=project_id,
            file_name=file.filename or "unknown",
            file_type=file_type,
            mime_type=file.content_type or "application/octet-stream",
            file_size=saved["size"],
            storage_type="local",
            storage_path=saved["storage_path"],
            file_url=saved["file_url"],
            meta_json={},
        )
        db.add(asset)
        db.commit()
        db.refresh(asset)
        return asset
