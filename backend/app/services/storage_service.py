import os
import uuid
from abc import ABC, abstractmethod
from pathlib import Path

import requests
from app.core.config import settings
from fastapi import UploadFile


class BaseStorageService(ABC):
    @abstractmethod
    def save_upload(self, file: UploadFile, sub_dir: str) -> dict:
        raise NotImplementedError

    @abstractmethod
    def save_bytes(self, content: bytes, filename: str, sub_dir: str) -> dict:
        raise NotImplementedError

    @abstractmethod
    def download_remote_file(self, url: str, sub_dir: str) -> dict:
        raise NotImplementedError

    @abstractmethod
    def build_public_url(self, relative_path: str) -> str:
        raise NotImplementedError


class LocalStorageService(BaseStorageService):
    def save_upload(self, file: UploadFile, sub_dir: str) -> dict:
        ext = os.path.splitext(file.filename or "")[1]
        filename = f"{uuid.uuid4().hex}{ext}"
        content = file.file.read()
        return self.save_bytes(content, filename, sub_dir)

    def save_bytes(self, content: bytes, filename: str, sub_dir: str) -> dict:
        relative_path = f"{sub_dir}/{filename}"
        full_path = Path(settings.upload_dir) / relative_path
        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_bytes(content)
        return {
            "relative_path": relative_path,
            "storage_path": str(full_path),
            "file_url": self.build_public_url(relative_path),
            "size": len(content),
        }

    def download_remote_file(self, url: str, sub_dir: str) -> dict:
        resp = requests.get(url, timeout=30)
        resp.raise_for_status()
        filename = f"{uuid.uuid4().hex}.mp4"
        return self.save_bytes(resp.content, filename, sub_dir)

    def build_public_url(self, relative_path: str) -> str:
        return f"{settings.public_base_url.rstrip('/')}/files/{relative_path}"


class OssStorageService(BaseStorageService):
    """预留 OSS 扩展，第一阶段不实现。"""

    def save_upload(self, file: UploadFile, sub_dir: str) -> dict:
        raise NotImplementedError("OSS storage is not implemented in phase-1")

    def save_bytes(self, content: bytes, filename: str, sub_dir: str) -> dict:
        raise NotImplementedError("OSS storage is not implemented in phase-1")

    def download_remote_file(self, url: str, sub_dir: str) -> dict:
        raise NotImplementedError("OSS storage is not implemented in phase-1")

    def build_public_url(self, relative_path: str) -> str:
        raise NotImplementedError("OSS storage is not implemented in phase-1")


storage_service = LocalStorageService()
