import axios from 'axios'
import http from '../utils/http'

export type UploadTicketReq = {
  file_name: string
  mime_type?: string
  file_size: number
}

export type UploadTicketData = {
  upload_url: string
  object_key: string
  file_url: string
  method: 'PUT'
  expire_at: number
  headers?: Record<string, string>
}

export type ConfirmUploadReq = {
  project_id?: number
  object_key: string
  file_name: string
  mime_type?: string
  file_size: number
}

export const getUploadTicketApi = (payload: UploadTicketReq, projectId?: number) => {
  const query = Number.isFinite(projectId) && Number(projectId) > 0 ? `?project_id=${projectId}` : ''
  return http.post(`/api/v1/assets/upload-ticket${query}`, payload)
}

export const confirmUploadApi = (payload: ConfirmUploadReq) => http.post('/api/v1/assets/confirm-upload', payload)

export const directUploadAssetApi = async (file: File, projectId?: number) => {
  // Step 1: ask backend for upload ticket + object key.
  const ticketResp = await getUploadTicketApi(
    {
      file_name: file.name || `upload_${Date.now()}`,
      mime_type: file.type || 'application/octet-stream',
      file_size: file.size
    },
    projectId
  )
  const ticket = ticketResp.data?.data as UploadTicketData | undefined
  if (!ticket?.upload_url || !ticket.object_key) {
    throw new Error('upload ticket invalid')
  }

  const uploadHeaders = { ...(ticket.headers || {}) }
  if (!Object.keys(uploadHeaders).some((k) => k.toLowerCase() === 'content-type')) {
    uploadHeaders['Content-Type'] = file.type || 'application/octet-stream'
  }

  // Step 2: upload binary directly to OSS by presigned URL.
  await axios.put(ticket.upload_url, file, { headers: uploadHeaders })

  // Step 3: notify backend to verify object and persist Asset record.
  const confirmPayload: ConfirmUploadReq = {
    object_key: ticket.object_key,
    file_name: file.name || 'upload.bin',
    mime_type: file.type || 'application/octet-stream',
    file_size: file.size
  }
  if (Number.isFinite(projectId) && Number(projectId) > 0) {
    confirmPayload.project_id = Number(projectId)
  }
  return confirmUploadApi(confirmPayload)
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
