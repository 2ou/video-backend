import http from '../utils/http'

export const uploadAssetApi = (file: File, projectId?: number) => {
  const formData = new FormData()
  formData.append('file', file)
  if (Number.isFinite(projectId) && Number(projectId) > 0) formData.append('project_id', String(projectId))
  return http.post('/api/v1/assets/upload', formData)
}

export const getAssetApi = (assetId: number) => http.get(`/api/v1/assets/${assetId}`)

export const reversePromptApi = (assetId: number, hint?: string) =>
  http.post('/api/v1/assets/reverse-prompt', { asset_id: assetId, hint })

export const polishPromptApi = (payload: { text: string; model: string; style_hint?: string }) =>
  http.post('/api/v1/assets/prompt-polish', payload)

export const generateImageApi = (
  payload: { model: string; prompt: string; negative_prompt?: string; aspect_ratio?: string },
  projectId?: number
) => {
  const query = projectId ? `?project_id=${projectId}` : ''
  return http.post(`/api/v1/assets/generate-image${query}`, payload)
}
