from datetime import datetime

from app.core.database import SessionLocal
from app.models.workflow_run import WorkflowRun
from app.models.workflow_run_node import WorkflowRunNode
from app.services.kie_service import kie_service
from app.services.storage_service import storage_service
from app.workers.celery_app import celery_app

MAX_ATTEMPTS = 60
RETRY_SECONDS = 5


@celery_app.task(name="app.workers.polling_tasks.poll_kie_task")
def poll_kie_task(run_node_id: int, attempt: int = 1) -> None:
    db = SessionLocal()
    try:
        run_node = db.get(WorkflowRunNode, run_node_id)
        if not run_node or not run_node.provider_task_id:
            return

        run = db.get(WorkflowRun, run_node.run_id)
        if not run:
            return

        if attempt > MAX_ATTEMPTS:
            run_node.status = "timeout"
            run_node.error_message = "Polling timeout"
            run_node.finished_at = datetime.utcnow()
            run.status = "timeout"
            run.finished_at = datetime.utcnow()
            db.commit()
            return

        raw = kie_service.query_task(run_node.provider_task_id)
        status = kie_service.normalize_status(raw.get("status") or raw.get("data", {}).get("status"))

        if status in {"queued", "running"}:
            poll_kie_task.apply_async(
                kwargs={"run_node_id": run_node_id, "attempt": attempt + 1},
                countdown=RETRY_SECONDS,
            )
            return

        if status == "success":
            urls = kie_service.extract_result_urls(raw)
            files = [storage_service.download_remote_file(url, "result") for url in urls]
            run_node.output_json = {"result_urls": [f["file_url"] for f in files]}
            run_node.status = "success"
            run_node.finished_at = datetime.utcnow()
            _refresh_run_status(db, run)
            db.commit()
            return

        run_node.status = "failed"
        run_node.error_message = raw.get("message", "KIE task failed")
        run_node.finished_at = datetime.utcnow()
        run.status = "failed"
        run.error_message = run_node.error_message
        run.finished_at = datetime.utcnow()
        db.commit()
    finally:
        db.close()


def _refresh_run_status(db, run: WorkflowRun) -> None:
    nodes = db.query(WorkflowRunNode).filter(WorkflowRunNode.run_id == run.id).all()
    statuses = [n.status for n in nodes]
    if any(s == "failed" for s in statuses):
        run.status = "failed"
        run.finished_at = datetime.utcnow()
    elif all(s in {"success", "skipped"} for s in statuses):
        run.status = "success"
        run.finished_at = datetime.utcnow()
        run.output_json = {
            "nodes": [{"node_id": n.node_id, "output_json": n.output_json} for n in nodes if n.output_json]
        }
    else:
        run.status = "running"
