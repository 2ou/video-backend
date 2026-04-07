import logging

import requests
from app.core.config import settings
from app.core.exceptions import ExternalServiceError

logger = logging.getLogger(__name__)


class KieService:
    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {settings.kie_api_key}", "Content-Type": "application/json"}

    def submit_video_task(self, payload: dict) -> str:
        try:
            logger.info("Submitting KIE video task")
            resp = requests.post(
                f"{settings.kie_base_url.rstrip('/')}/v1/video/tasks",
                json=payload,
                headers=self._headers(),
                timeout=30,
            )
            resp.raise_for_status()
            body = resp.json()
            task_id = body.get("task_id") or body.get("data", {}).get("task_id")
            if not task_id:
                raise ExternalServiceError("KIE submit response missing task_id", code="KIE_BAD_RESPONSE")
            return task_id
        except requests.RequestException as exc:
            logger.error("KIE submit failed: %s", str(exc))
            raise ExternalServiceError("KIE submit failed", code="KIE_SUBMIT_FAILED") from exc

    def query_task(self, provider_task_id: str) -> dict:
        try:
            resp = requests.get(
                f"{settings.kie_base_url.rstrip('/')}/v1/video/tasks/{provider_task_id}",
                headers=self._headers(),
                timeout=30,
            )
            resp.raise_for_status()
            return resp.json()
        except requests.RequestException as exc:
            logger.error("KIE query failed for task=%s", provider_task_id)
            raise ExternalServiceError("KIE query failed", code="KIE_QUERY_FAILED") from exc

    def normalize_status(self, raw_status: str) -> str:
        status = (raw_status or "").lower()
        if status in {"queued", "pending", "waiting"}:
            return "queued"
        if status in {"running", "processing"}:
            return "running"
        if status in {"success", "succeeded", "completed", "done"}:
            return "success"
        if status in {"failed", "error", "canceled"}:
            return "failed"
        return "running"

    def extract_result_urls(self, raw_response: dict) -> list[str]:
        data = raw_response.get("data", raw_response)
        urls: list[str] = []
        if isinstance(data.get("result_urls"), list):
            urls.extend(data["result_urls"])
        if data.get("result_url"):
            urls.append(data["result_url"])
        return urls


kie_service = KieService()
