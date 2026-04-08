

export const useCanvasStore = defineStore('canvas', {
  state: () => ({ canvas: { nodes: [], edges: [], viewport: { x: 0, y: 0, zoom: 1 } } as any }),
  actions: {
    async loadCanvas(projectId: number) {
      const res = await getCanvasApi(projectId)
      this.canvas = res.data.data.canvas_json
    },
    async saveCanvas(projectId: number) {
      await saveCanvasApi(projectId, this.canvas)
    }
  }
})
