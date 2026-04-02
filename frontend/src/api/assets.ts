import http from '../utils/http'

export const uploadAssetApi = (file: File, projectId?: number) => {
  const formData = new FormData()
  formData.append('file', file)
  if (projectId) formData.append('project_id', String(projectId))
  return http.post('/api/v1/assets/upload', formData)
}
