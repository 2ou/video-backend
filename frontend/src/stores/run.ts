import { defineStore } from 'pinia'
import { getRunApi, getRunNodesApi, runProjectApi } from '../api/projects'


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
      this.nodes = (nodeRes.data.data || []).map((node: any) => ({
        ...node,
        input_json: parseJson(node.input_json),
        output_json: parseJson(node.output_json)
      }))
    }
  }
})

const parseJson = (value: any) => {
  if (!value) return {}
  if (typeof value === 'object') return value
  if (typeof value !== 'string') return {}
  try {
    return JSON.parse(value)
  } catch {
    return {}
  }
}
