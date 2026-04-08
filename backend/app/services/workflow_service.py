from datetime import datetime

from app.core.exceptions import ValidationError
from app.models.project import Project
from app.models.project_version import ProjectVersion
from app.models.workflow_run import WorkflowRun
from app.models.workflow_run_node import WorkflowRunNode
from app.workers.run_tasks import execute_run
from sqlalchemy.orm import Session

SUPPORTED_NODE_TYPES = {"input_video", "prompt_input", "kie_video_task", "output_video"}


class WorkflowService:
    @staticmethod
    def create_and_run(db: Session, user_id: int, project: Project) -> WorkflowRun:
        version = db.get(ProjectVersion, project.latest_version_id) if project.latest_version_id else None
        if not version:
            raise ValidationError("请先保存画布")

        canvas = version.canvas_json or {}
        nodes = canvas.get("nodes", [])

        run = WorkflowRun(
            user_id=user_id,
            project_id=project.id,
            version_id=version.id,
            status="queued",
            input_json={"canvas_node_count": len(nodes)},
            started_at=datetime.utcnow(),
        )
        db.add(run)
        db.flush()

        for node in nodes:
            node_type = node.get("type")
            if node_type not in SUPPORTED_NODE_TYPES:
                continue
            run_node = WorkflowRunNode(
                run_id=run.id,
                node_id=node.get("id", ""),
                node_type=node_type,
                status="queued",
                input_json=node.get("data", {}),
            )
            db.add(run_node)

        db.commit()
        db.refresh(run)
        execute_run.delay(run.id)
        return run
