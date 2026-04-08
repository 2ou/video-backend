import http from '../utils/http'

export const listProjectsApi = () => http.get('/api/v1/projects')
export const createProjectApi = (payload: { name: string; description: string }) => http.post('/api/v1/projects', payload)
export const getCanvasApi = (projectId: number) => http.get(`/api/v1/projects/${projectId}/canvas`)
export const saveCanvasApi = (projectId: number, canvas_json: any) =>
  http.put(`/api/v1/projects/${projectId}/canvas`, { canvas_json, remark: 'save from frontend' })
export const runProjectApi = (projectId: number) => http.post(`/api/v1/projects/${projectId}/run`)
export const getRunApi = (runId: number) => http.get(`/api/v1/runs/${runId}`)
export const getRunNodesApi = (runId: number) => http.get(`/api/v1/runs/${runId}/nodes`)
