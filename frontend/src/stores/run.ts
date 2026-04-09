import { defineStore } from 'pinia'
import { getRunApi, getRunNodesApi, runProjectApi } from '../api/projects'
import { PORT_IDS } from '../constants/workflow'

/**
 * 运行前图编译（Graph Compiler）：
 * - 遍历 edges，回溯 VideoGenNode 的上游输入。
 * - 将上游 Text/Image/Audio 的值聚合到 VideoGenNode.data.input_payload。
 *
 * 说明：
 * 1. 不改变数据库结构，只改 node.data 内容。
 * 2. 编译结果会随着 saveCanvas 一起持久化到 version.canvas_json。
 */
export const compileGraphForRun = (canvas: any) => {
  if (!canvas || typeof canvas !== 'object') return canvas
  if (!Array.isArray(canvas.nodes)) canvas.nodes = []
  if (!Array.isArray(canvas.edges)) canvas.edges = []

  const nodes = canvas.nodes
  const edges = canvas.edges

  const nodeById = new Map<string, any>()
  nodes.forEach((node: any) => nodeById.set(String(node?.id || ''), node))

  const incomingEdgesMap = new Map<string, any[]>()
  for (const edge of edges) {
    const targetId = String(edge?.target || '')
    if (!targetId) continue
    const current = incomingEdgesMap.get(targetId) || []
    current.push(edge)
    incomingEdgesMap.set(targetId, current)
  }

  for (const node of nodes) {
    if (node?.type !== 'video_gen') continue
    if (!node.data || typeof node.data !== 'object') node.data = {}

    const incoming = incomingEdgesMap.get(String(node.id)) || []
    const inputPayload: Record<string, any> = {}

    // 透传 VideoGen 自身的核心参数，后端只取 input_payload 即可。
    inputPayload.model = node.data.model
    inputPayload.mode = node.data.mode
    inputPayload.resolution = node.data.resolution
    inputPayload.aspect_ratio = node.data.aspect_ratio
    inputPayload.duration = node.data.duration
    inputPayload.fps = node.data.fps
    inputPayload.negative_prompt = node.data.negative_prompt
    inputPayload.audio_sync = Boolean(node.data.audio_sync)

    // prompt_in -> TextNode.text
    const promptEdge = incoming.find((edge) => edge?.targetHandle === PORT_IDS.PROMPT_IN)
    if (promptEdge) {
      const sourceNode = nodeById.get(String(promptEdge.source || ''))
      const promptText = String(sourceNode?.data?.text || '').trim()
      if (promptText) inputPayload.prompt = promptText
    }

    // first_frame_in -> ImageNode.asset_id
    const firstFrameEdge = incoming.find((edge) => edge?.targetHandle === PORT_IDS.FIRST_FRAME_IN)
    if (firstFrameEdge) {
      const sourceNode = nodeById.get(String(firstFrameEdge.source || ''))
      const assetId = sourceNode?.data?.asset_id
      if (assetId !== null && assetId !== undefined && String(assetId) !== '') {
        inputPayload.first_frame_asset_id = assetId
      }
    }

    // last_frame_in -> ImageNode.asset_id
    const lastFrameEdge = incoming.find((edge) => edge?.targetHandle === PORT_IDS.LAST_FRAME_IN)
    if (lastFrameEdge) {
      const sourceNode = nodeById.get(String(lastFrameEdge.source || ''))
      const assetId = sourceNode?.data?.asset_id
      if (assetId !== null && assetId !== undefined && String(assetId) !== '') {
        inputPayload.last_frame_asset_id = assetId
      }
    }

    // multi_image_in -> 多个 ImageNode.asset_id
    const multiImageEdges = incoming.filter((edge) => edge?.targetHandle === PORT_IDS.MULTI_IMAGE_IN)
    if (multiImageEdges.length) {
      const ids = multiImageEdges
        .map((edge) => nodeById.get(String(edge.source || ''))?.data?.asset_id)
        .filter((id) => id !== null && id !== undefined && String(id) !== '')
      if (ids.length) inputPayload.multi_image_ids = ids
    }

    // audio_in -> AudioNode.asset_id
    const audioEdge = incoming.find((edge) => edge?.targetHandle === PORT_IDS.AUDIO_IN)
    if (audioEdge) {
      const sourceNode = nodeById.get(String(audioEdge.source || ''))
      const assetId = sourceNode?.data?.asset_id
      if (assetId !== null && assetId !== undefined && String(assetId) !== '') {
        inputPayload.drive_audio_asset_id = assetId
      }
    }

    node.data.input_payload = inputPayload
  }

  return canvas
}

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
