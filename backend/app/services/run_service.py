from sqlalchemy.orm import Session

from app.core.exceptions import NotFoundError
from app.models.workflow_run import WorkflowRun
from app.models.workflow_run_node import WorkflowRunNode


class RunService:
    @staticmethod
    def get_run(db: Session, run_id: int, user_id: int) -> WorkflowRun:
        run = db.get(WorkflowRun, run_id)
        if not run or run.user_id != user_id:
            raise NotFoundError("运行记录不存在")
        return run

    @staticmethod
    def get_nodes(db: Session, run_id: int) -> list[WorkflowRunNode]:
        return (
            db.query(WorkflowRunNode)
            .filter(WorkflowRunNode.run_id == run_id)
            .order_by(WorkflowRunNode.id.asc())
            .all()
        )

    @staticmethod
    def is_finished(run: WorkflowRun) -> bool:
        return run.status in {"success", "failed", "timeout"}
