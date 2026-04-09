import { defineStore } from 'pinia'
import { getCanvasApi, saveCanvasApi } from '../api/projects'
import { ensureNodeDataDefaults } from '../constants/workflow'

const createEmptyCanvas = () => ({
  nodes: [],
  edges: [],
  viewport: { x: 0, y: 0, zoom: 1 }
})

const normalizeCanvas = (canvasLike: any) => {
  const normalized = {
    nodes: Array.isArray(canvasLike?.nodes) ? canvasLike.nodes : [],
    edges: Array.isArray(canvasLike?.edges) ? canvasLike.edges : [],
    viewport:
      canvasLike?.viewport && typeof canvasLike.viewport === 'object'
        ? canvasLike.viewport
        : { x: 0, y: 0, zoom: 1 }
  }

  normalized.nodes.forEach((node: any) => ensureNodeDataDefaults(node))
  return normalized
}

export const useCanvasStore = defineStore('canvas', {
  state: () => ({ canvas: createEmptyCanvas() as any }),
  actions: {
    async loadCanvas(projectId: number) {
      const res = await getCanvasApi(projectId)
      this.canvas = normalizeCanvas(res.data.data.canvas_json)
    },
    async saveCanvas(projectId: number) {
      this.canvas = normalizeCanvas(this.canvas)
      await saveCanvasApi(projectId, this.canvas)
    }
  }
})
