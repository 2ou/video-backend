<template>
  <div style="height: 100vh; display:flex; flex-direction:column">
    <div style="padding: 8px; border-bottom:1px solid #ddd">
      <el-button @click="save">保存</el-button>
      <el-button type="primary" @click="run">运行</el-button>
      <el-upload :show-file-list="false" :http-request="uploadReq" style="display:inline-block; margin-left: 12px">
        <el-button>上传素材</el-button>
      </el-upload>
    </div>
    <div style="display:flex; flex:1">
      <div style="width:220px; padding:8px"><NodePalette @add="addNode" /></div>
      <div style="flex:1">
        <VueFlow v-model:nodes="canvasStore.canvas.nodes" v-model:edges="canvasStore.canvas.edges" @node-click="onNodeClick">
          <Background />
          <Controls />
        </VueFlow>
      </div>
      <div style="width:300px; padding:8px"><NodePropsPanel :selected="selected" /></div>
    </div>
  </div>
</template>

<script setup lang="ts">

const route = useRoute()
const router = useRouter()
const canvasStore = useCanvasStore()
const runStore = useRunStore()
const selected = ref<any>(null)
const projectId = Number(route.params.id)

onMounted(() => canvasStore.loadCanvas(projectId))

const addNode = (type: string) => {
  canvasStore.canvas.nodes.push({
    id: `${type}_${Date.now()}`,
    type,
    position: { x: 220, y: 220 },
    data: { label: type }
  })
}

const onNodeClick = ({ node }: any) => (selected.value = node)
const save = () => canvasStore.saveCanvas(projectId)
const run = async () => {
  const runId = await runStore.runProject(projectId)
  router.push(`/runs/${runId}`)
}

const uploadReq = async (opt: any) => {
  const res = await uploadAssetApi(opt.file, projectId)
  const videoNode = canvasStore.canvas.nodes.find((n: any) => n.type === 'input_video')
  if (videoNode) videoNode.data.asset_id = res.data.data.id
}
</script>
