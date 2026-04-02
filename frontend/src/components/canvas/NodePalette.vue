<template>
  <el-card>
    <div>节点列表</div>
    <el-button v-for="n in nodeTypes" :key="n" style="margin:6px 0" @click="$emit('add', n)">{{ n }}</el-button>
  </el-card>
</template>
<script setup lang="ts">
const nodeTypes = ['input_video', 'prompt_input', 'kie_video_task', 'output_video']
defineEmits<{ add: [string] }>()
</script>
