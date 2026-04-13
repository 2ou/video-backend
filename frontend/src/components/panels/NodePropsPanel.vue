<template>
  <div class="props-panel-container" v-if="node">
    <div class="panel-header">
      <el-icon><Setting /></el-icon>
      <span>节点配置 ({{ node.type.toUpperCase() }})</span>
    </div>

    <div class="panel-body">
      <div class="prop-group">
        <label>生成模型</label>
        <el-select
            v-model="node.data.modelName"
            placeholder="请选择 AI 模型"
            :disabled="isGenerating"
            class="dark-input"
        >
          <el-option label="Kling 3.0 (视频)" value="kling-3.0/video" />
          <el-option label="Seedance 2.0 (字节)" value="bytedance/seedance-2" />
          <el-option label="Nano Banana Pro (图片)" value="nano-banana-pro" />
        </el-select>
      </div>

      <div class="prop-group">
        <label>Prompt 提示词</label>
        <el-input
            v-model="node.data.params.prompt"
            type="textarea"
            :rows="5"
            placeholder="描述你想要生成的画面..."
            :disabled="isGenerating"
            class="dark-input"
        />
      </div>

      <div v-if="node.data.modelName?.includes('kling')" class="prop-group inline">
        <label>音画同步生成</label>
        <el-switch v-model="node.data.params.sound" :disabled="isGenerating" />
      </div>

      <div v-if="isGenerating" class="task-status-card">
        <div class="loading-spinner">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <div class="task-info">
          <p class="status-text">正在通过云端生成中...</p>
          <p class="task-id">ID: {{ currentTaskId }}</p>
        </div>
      </div>
    </div>

    <div class="panel-footer">
      <el-button
          type="primary"
          class="submit-btn"
          :loading="isGenerating"
          @click="submitJob"
      >
        {{ isGenerating ? '生成中...' : '立即启动生成' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onBeforeUnmount } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Setting, Loading } from '@element-plus/icons-vue'

const { getSelectedNodes } = useVueFlow()
const node = computed(() => getSelectedNodes.value[0])

const isGenerating = ref(false)
const currentTaskId = ref('')
let pollingTimer: number | null = null

// 核心业务：提交任务并开启状态轮询
const submitJob = async () => {
  if (!node.value) return

  isGenerating.value = true
  // 立即将节点状态设为 PENDING，ImageNode.vue 会自动显示 Loading 遮罩
  node.value.data.status = 'PENDING'

  try {
    // 1. 调用后端接口启动任务
    const response = await axios.post('/api/v1/jobs/generate', {
      modelName: node.value.data.modelName,
      params: {
        ...node.value.data.params,
        image_url: node.value.data.url // 自动提取当前节点的图片
      }
    })

    // 假设后端返回数据结构为 { code: "OK", data: "task_123..." }
    currentTaskId.value = response.data.data

    // 2. 启动轮询：每 5 秒查询一次 MySQL 中的任务状态
    startPolling(currentTaskId.value)

  } catch (error) {
    console.error('Submit Failed:', error)
    ElMessage.error('生成任务启动失败，请检查网络或后端配置')
    resetState()
  }
}

const startPolling = (taskId: string) => {
  pollingTimer = window.setInterval(async () => {
    try {
      const res = await axios.get(`/api/v1/jobs/status/${taskId}`)
      const { status, video_url } = res.data.data

      if (status === 'SUCCESS') {
        stopPolling()
        // 更新节点数据，ImageNode.vue 会检测到并替换为播放器
        node.value.data.status = 'SUCCESS'
        node.value.data.videoUrl = video_url
        isGenerating.value = false
        ElMessage.success('视频已成功生成！')
      } else if (status === 'FAILED') {
        stopPolling()
        ElMessage.error('视频生成失败')
        resetState()
      }
    } catch (e) {
      console.warn('轮询中...', e)
    }
  }, 5000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const resetState = () => {
  stopPolling()
  isGenerating.value = false
  if (node.value) node.value.data.status = 'IDLE'
}

// 卸载组件前清理定时器
onBeforeUnmount(() => stopPolling())

</script>

<style scoped>
.props-panel-container {
  height: 100%;
  background: #1e1e1e; /* 深灰色背景 */
  color: #e0e0e0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px;
  background: #252526;
  border-bottom: 1px solid #333;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 14px;
}

.panel-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.prop-group {
  margin-bottom: 24px;
}

.prop-group label {
  display: block;
  font-size: 12px;
  color: #888;
  margin-bottom: 8px;
}

.prop-group.inline {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 暗黑输入框定制 */
:deep(.dark-input .el-textarea__inner),
:deep(.dark-input .el-input__wrapper) {
  background-color: #2d2d2d !important;
  border-color: #3f3f3f !important;
  color: #fff !important;
  box-shadow: none !important;
}

.task-status-card {
  margin-top: 20px;
  background: #252526;
  border: 1px solid #409eff;
  border-radius: 6px;
  padding: 15px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.loading-spinner {
  font-size: 24px;
  color: #409eff;
}

.status-text {
  font-size: 13px;
  margin: 0;
  color: #409eff;
}

.task-id {
  font-size: 11px;
  color: #666;
  margin: 4px 0 0 0;
}

.panel-footer {
  padding: 20px;
  background: #252526;
  border-top: 1px solid #333;
}

.submit-btn {
  width: 100%;
  height: 40px;
  font-weight: bold;
  letter-spacing: 1px;
}
</style>