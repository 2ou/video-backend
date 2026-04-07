import {defineStore} from 'pinia'
import {getRunApi, getRunNodesApi, runProjectApi} from '../api/projects'

export const useRunStore = defineStore('run', {
  state: () => ({ run: null as any, nodes: [] as any[] }),
  actions: {
    async runProject(projectId: number) {
      const res = await runProjectApi(projectId)
      const runId = res.data.data.run_id
      await this.loadRun(runId)
      return runId
    },
    async loadRun(runId: number) {
      const [runRes, nodeRes] = await Promise.all([getRunApi(runId), getRunNodesApi(runId)])
      this.run = runRes.data.data
      this.nodes = nodeRes.data.data
    }
  }
})
