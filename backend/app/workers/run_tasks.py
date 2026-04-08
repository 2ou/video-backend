from datetime import datetime

from app.core.database import SessionLocal
from app.models.project_version import ProjectVersion
from app.models.workflow_run import WorkflowRun
from app.models.workflow_run_node import WorkflowRunNode
from app.services.kie_service import kie_service
from app.workers.celery_app import celery_app
from app.workers.polling_tasks import poll_kie_task


@celery_app.task(name="app.workers.run_tasks.execute_run")
def execute_run(run_id: int) -> None:
    db = SessionLocal()
    try:
        run = db.get(WorkflowRun, run_id)
        if not run:
            return
        version = db.get(ProjectVersion, run.version_id)
        if not version:
            run.status = "failed"
            run.error_message = "version not found"
            db.commit()
            return

        canvas = version.canvas_json
        nodes_map = {n["id"]: n for n in canvas.get("nodes", [])}
        run_nodes = db.query(WorkflowRunNode).filter(WorkflowRunNode.run_id == run_id).all()

        for run_node in run_nodes:
            if run_node.node_type != "kie_video_task":
                if run_node.node_type in {"input_video", "prompt_input", "output_video"}:
                    run_node.status = "success"
                continue

            payload = {
                "model": run_node.input_json.get("params", {}).get("model", "video-model-a"),
                "params": run_node.input_json.get("params", {}),
                "prompt": _find_prompt(canvas),
                "input_asset_id": _find_asset_id(canvas),
            }
            provider_task_id = kie_service.submit_video_task(payload)
            run_node.provider = "kie"
            run_node.provider_task_id = provider_task_id
            run_node.status = "running"
            run_node.started_at = datetime.utcnow()
            db.commit()
            poll_kie_task.apply_async(kwargs={"run_node_id": run_node.id, "attempt": 1}, countdown=3)

        run.status = "running"
        db.commit()
    finally:
        db.close()


def _find_prompt(canvas: dict) -> str:
    for node in canvas.get("nodes", []):
        if node.get("type") == "prompt_input":
            return node.get("data", {}).get("text", "")
    return ""


def _find_asset_id(canvas: dict) -> int | None:
    for node in canvas.get("nodes", []):
        if node.get("type") == "input_video":
            return node.get("data", {}).get("asset_id")
    return None
