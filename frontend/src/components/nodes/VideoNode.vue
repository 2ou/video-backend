<template>
  <div class="video-node" :class="{ 'is-selected': selected }">
    <Handle type="target" position="left" />
    <video :src="data.url" controls muted />
    <div class="label" v-if="data.modelName">{{ data.modelName }}</div>
    <Handle type="source" position="right" />
  </div>
</template>

<script setup lang="ts">
import { Handle } from '@vue-flow/core'
defineProps(['data', 'selected'])
</script>

<style scoped>
.video-node {
  width: 200px;
  background: #000;
  border: 2px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
}
.video-node.is-selected { border-color: #e6a23c; } /* 选中视频节点显橙色 */
.video-node video { width: 100%; display: block; }
.label { font-size: 10px; background: rgba(0,0,0,0.7); color: #fff; text-align: center; }
</style>