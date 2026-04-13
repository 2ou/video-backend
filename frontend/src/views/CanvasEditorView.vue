<template>
  <div class="canvas-editor-page">
    <div class="top-nav">
      <el-button link @click="$router.push('/projects')">← 返回列表</el-button>
      <div class="proj-title">項目編輯器</div>
      <el-button type="primary" size="small">保存畫布</el-button>
    </div>

    <div class="main-content">
      <div class="left-bar">
        <div class="tool-btn" @click="addNewNode('image')">🖼️ 添加圖片節點</div>
        <div class="tool-btn" @click="addNewNode('text')">📝 添加文本節點</div>
      </div>

      <div class="canvas-area">
        <VueFlow v-model:nodes="nodes" v-model:edges="edges">
          <template #node-image="props">
            <ImageNode v-bind="props" />
          </template>
          <Background pattern-color="#222" :gap="20" />
        </VueFlow>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import ImageNode from '../components/nodes/ImageNode.vue'

const { addNodes, project } = useVueFlow()
const nodes = ref([])
const edges = ref([])

const addNewNode = (type: string) => {
  addNodes([{
    id: `node_${Date.now()}`,
    type: type,
    position: { x: 250, y: 150 },
    data: {
      url: 'https://via.placeholder.com/200x120/222/888?text=New+Image',
      status: 'IDLE',
      modelName: 'kling-3.0/video',
      params: { prompt: '' }
    }
  }])
}
</script>

<style scoped>
.canvas-editor-page { height: 100vh; display: flex; flex-direction: column; overflow: hidden; }
.top-nav { height: 50px; background: #151518; border-bottom: 1px solid #2b2b30; display: flex; align-items: center; padding: 0 20px; justify-content: space-between; }
.proj-title { color: #fff; font-weight: 600; }

.main-content { flex: 1; display: flex; }
.left-bar { width: 200px; background: #111; border-right: 1px solid #2b2b30; padding: 15px; }
.tool-btn {
  padding: 12px; background: #1a1a1a; color: #ccc; border-radius: 6px;
  margin-bottom: 10px; cursor: pointer; text-align: center; border: 1px solid #333;
}
.tool-btn:hover { border-color: #409eff; color: #409eff; }

.canvas-area { flex: 1; position: relative; background: #0a0a0c; }
</style>