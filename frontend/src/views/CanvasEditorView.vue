<template>
  <div class="editor-shell">
    <header class="editor-topbar">
      <div class="top-left">
        <div class="brand-mark">LibTV</div>
        <div class="project-meta">项目 {{ projectId }} / 工作流画布</div>
      </div>

      <div class="top-actions">
        <el-upload :show-file-list="false" :http-request="uploadReq">
          <el-button type="info" plain>上传素材</el-button>
        </el-upload>
        <el-button @click="save">保存</el-button>
        <el-button type="primary" @click="run">运行</el-button>
      </div>
    </header>

    <div class="editor-main">
      <section
          ref="canvasRef"
          class="editor-canvas"
          @click="onCanvasClick"
          @dblclick.prevent="onCanvasDoubleClick"
      >
        <VueFlow
            v-model:nodes="canvasStore.canvas.nodes"
            v-model:edges="canvasStore.canvas.edges"
            class="workflow-flow"
            :node-types="nodeTypes"
            :default-viewport="canvasStore.canvas.viewport"
            :zoom-on-double-click="false"
            :min-zoom="0.2"
            :max-zoom="2"
            fit-view-on-init
            @pane-ready="onPaneReady"
        >
          <Background :gap="26" :size="1.2" pattern-color="rgba(97, 112, 157, 0.38)" />
          <Controls position="bottom-left" />
        </VueFlow>

        <div class="canvas-tip">左键双击空白画布添加节点</div>

        <div
            v-if="nodePicker.visible"
            class="node-picker"
            :style="{ left: `${nodePicker.x}px`, top: `${nodePicker.y}px` }"
            @click.stop
        >
          <div class="picker-title">选择要添加的节点</div>
          <button
              v-for="item in pickableNodes"
              :key="item.type"
              type="button"
              class="picker-item"
              :style="{ '--picker-color': item.accent }"
              @click="handleNodePicked(item.type)"
          >
            <span class="picker-icon">{{ item.icon }}</span>
            <span class="picker-info">
              <span>{{ item.title }}</span>
              <small>{{ item.subtitle }}</small>
            </span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage } from 'element-plus'
import type { VueFlowStore } from '@vue-flow/core'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { useRoute, useRouter } from 'vue-router'
import BasicNode from '../components/nodes/BasicNode.vue'
import { useCanvasStore } from '../stores/canvas'
import { useRunStore } from '../stores/run'
import { directUploadAssetApi } from '../api/assets'
import {
  NODE_CATALOG,
  SELECTABLE_NODE_TYPES,
  createDefaultNodeData,
  ensureNodeDataDefaults,
  type WorkflowNodeType
} from '../constants/workflow'

const route = useRoute()
const router = useRouter()
const canvasStore = useCanvasStore()
const runStore = useRunStore()
const projectId = Number(route.params.id)

const canvasRef = ref<HTMLElement | null>(null)
const flowStore = ref<VueFlowStore | null>(null)

const nodeTypes = {
  prompt_input: BasicNode,
  input_video: BasicNode,
  kie_video_task: BasicNode,
  output_video: BasicNode
}

const pickableNodes = NODE_CATALOG.filter((item) => SELECTABLE_NODE_TYPES.includes(item.type))

const nodePicker = reactive({
  visible: false,
  x: 0,
  y: 0,
  position: { x: 260, y: 200 }
})

onMounted(async () => {
  await canvasStore.loadCanvas(projectId)
  normalizeCanvas()
})

const normalizeCanvas = () => {
  if (!canvasStore.canvas || typeof canvasStore.canvas !== 'object') {
    canvasStore.canvas = { nodes: [], edges: [], viewport: { x: 0, y: 0, zoom: 1 } }
  }
  if (!Array.isArray(canvasStore.canvas.nodes)) canvasStore.canvas.nodes = []
  if (!Array.isArray(canvasStore.canvas.edges)) canvasStore.canvas.edges = []
  if (!canvasStore.canvas.viewport) canvasStore.canvas.viewport = { x: 0, y: 0, zoom: 1 }

  canvasStore.canvas.nodes.forEach((node: any) => ensureNodeDataDefaults(node))
}

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

const nextNodeId = (type: WorkflowNodeType) => `${type}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

const getDefaultInsertPosition = () => {
  if (!flowStore.value || !canvasRef.value) return { x: 260, y: 220 }
  const rect = canvasRef.value.getBoundingClientRect()
  return flowStore.value.screenToFlowCoordinate({
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2
  })
}

// 终极修复点：强制触发 VueFlow 更新与 Pinia 响应式更新
const addNode = (type: WorkflowNodeType, position?: { x: number; y: number }) => {
  const node = {
    id: nextNodeId(type),
    type,
    position: {
      x: position?.x || getDefaultInsertPosition().x,
      y: position?.y || getDefaultInsertPosition().y
    },
    data: createDefaultNodeData(type)
  }

  // 1. 最稳妥的渲染方式：直接调用 Vue Flow 内部实例 API
  if (flowStore.value && typeof flowStore.value.addNodes === 'function') {
    flowStore.value.addNodes([node])
  }

  // 2. 强制触发 Pinia store 的更新，使用展开运算符产生新数组以确保触发视图响应
  const currentNodes = Array.isArray(canvasStore.canvas.nodes) ? canvasStore.canvas.nodes : []
  if (!currentNodes.some((n: any) => n.id === node.id)) {
    canvasStore.canvas.nodes = [...currentNodes, node]
  }
}

const isBlankCanvasTarget = (target: EventTarget | null) => {
  if (!(target instanceof HTMLElement)) return false
  if (target.closest('.node-picker')) return false
  if (target.closest('.vue-flow__node')) return false
  if (target.closest('.vue-flow__controls')) return false
  if (target.closest('.vue-flow__edge')) return false
  return true
}

const openNodePicker = (event: MouseEvent) => {
  if (!canvasRef.value) return

  const rect = canvasRef.value.getBoundingClientRect()
  const maxX = Math.max(14, rect.width - 240)
  const maxY = Math.max(14, rect.height - 220)

  let flowX = 0
  let flowY = 0

  if (flowStore.value && typeof flowStore.value.screenToFlowCoordinate === 'function') {
    const coords = flowStore.value.screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
    flowX = coords.x
    flowY = coords.y
  } else {
    flowX = clamp(event.clientX - rect.left, 0, rect.width)
    flowY = clamp(event.clientY - rect.top, 0, rect.height)
  }

  nodePicker.visible = true
  // 解构生成新对象，避免由于底层代理导致的坐标引用错误
  nodePicker.position = { x: flowX, y: flowY }
  nodePicker.x = clamp(event.clientX - rect.left, 14, maxX)
  nodePicker.y = clamp(event.clientY - rect.top, 14, maxY)
}

const onCanvasClick = () => {
  hideNodePicker()
}

const onCanvasDoubleClick = (event: MouseEvent) => {
  if (event.button !== 0) return
  if (!isBlankCanvasTarget(event.target)) return
  openNodePicker(event)
}

const onPaneReady = (store: VueFlowStore) => {
  flowStore.value = store
}

const handleNodePicked = (type: WorkflowNodeType) => {
  // 传入拷贝的坐标点，确保安全
  addNode(type, { ...nodePicker.position })
  hideNodePicker()
}

const hideNodePicker = () => {
  nodePicker.visible = false
}

const save = async () => {
  await canvasStore.saveCanvas(projectId)
  ElMessage.success('画布已保存')
}

const run = async () => {
  await save()
  const runId = await runStore.runProject(projectId)
  router.push(`/runs/${runId}`)
}

const uploadReq = async (opt: UploadRequestOptions) => {
  try {
    const res = await directUploadAssetApi(opt.file as File, projectId)
    const data = res.data?.data || {}
    const assetId = data.asset_id || data.id
    const fileUrl = data.file_url || ''
    if (!assetId) {
      throw new Error('upload response missing asset id')
    }

    const imageNode = canvasStore.canvas.nodes.find((node: any) => node.type === 'input_video')
    if (imageNode) {
      ensureNodeDataDefaults(imageNode)
      imageNode.data.asset_id = assetId
      if (fileUrl) imageNode.data.asset_url = fileUrl
    }

    ElMessage.success(`素材上传成功: #${assetId}`)
    opt.onSuccess?.(res.data)
  } catch (error) {
    const message =
      (error as any)?.response?.data?.message ||
      (error as any)?.response?.data?.code ||
      (error as any)?.message ||
      'request failed'
    ElMessage.error(`素材上传失败: ${message}`)
    opt.onError?.(error as Error)
  }
}
</script>

<style scoped>
.editor-shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
      radial-gradient(circle at 25% 20%, rgba(28, 40, 68, 0.45), transparent 42%),
      radial-gradient(circle at 75% 80%, rgba(19, 24, 44, 0.6), transparent 44%),
      #070a11;
  color: #eef2ff;
  font-family: 'IBM Plex Sans', 'PingFang SC', 'Noto Sans SC', 'Segoe UI', sans-serif;
}

.editor-topbar {
  height: 56px;
  border-bottom: 1px solid rgba(89, 104, 148, 0.36);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  background: rgba(6, 10, 19, 0.85);
  backdrop-filter: blur(10px);
}

.top-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.brand-mark {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.project-meta {
  font-size: 12px;
  color: rgba(202, 211, 242, 0.82);
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-main {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
}

.editor-canvas {
  position: relative;
  border-radius: 20px;
  border: 1px solid rgba(94, 110, 154, 0.34);
  overflow: hidden;
  background-color: #090d17;
  background-image: radial-gradient(circle, rgba(89, 105, 152, 0.46) 1px, transparent 1px);
  background-size: 22px 22px;
}

.workflow-flow {
  width: 100%;
  height: 100%;
}

.canvas-tip {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(128, 146, 196, 0.35);
  background: rgba(11, 17, 29, 0.78);
  color: rgba(219, 229, 255, 0.9);
  pointer-events: none;
  z-index: 4;
}

.node-picker {
  position: absolute;
  z-index: 9999;
  width: 230px;
  border-radius: 14px;
  border: 1px solid rgba(123, 140, 194, 0.45);
  background: rgba(14, 20, 34, 0.97);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45);
  padding: 10px;
}

.picker-title {
  font-size: 12px;
  color: rgba(205, 215, 247, 0.78);
  margin-bottom: 8px;
}

.picker-item {
  width: 100%;
  margin-bottom: 8px;
  border-radius: 10px;
  border: 1px solid color-mix(in oklab, var(--picker-color) 38%, #40547f 62%);
  background: rgba(18, 26, 42, 0.94);
  color: #edf2ff;
  padding: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.picker-item:last-child {
  margin-bottom: 0;
}

.picker-icon {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: color-mix(in oklab, var(--picker-color) 42%, #121a2f 58%);
}

.picker-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.picker-info span {
  font-size: 12px;
  font-weight: 600;
}

.picker-info small {
  margin-top: 1px;
  font-size: 11px;
  color: rgba(196, 209, 247, 0.74);
}

:deep(.vue-flow__background-pattern) {
  opacity: 0.45;
}

:deep(.vue-flow__edge-path) {
  stroke: rgba(177, 197, 255, 0.6);
  stroke-width: 1.7;
}

:deep(.vue-flow__controls) {
  box-shadow: 0 0 0 1px rgba(120, 136, 186, 0.35);
  border-radius: 10px;
  overflow: hidden;
}

:deep(.vue-flow__controls-button) {
  background: rgba(12, 18, 31, 0.88);
  color: #e7eeff;
  border-color: rgba(108, 126, 178, 0.45);
}

:deep(.el-button--info.is-plain) {
  --el-button-bg-color: rgba(18, 26, 43, 0.78);
  --el-button-border-color: rgba(102, 124, 180, 0.6);
  --el-button-text-color: #e6edff;
}

@media (max-width: 1024px) {
  .editor-canvas {
    min-height: 420px;
  }
}
</style>
