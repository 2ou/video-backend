<template>
  <el-card>
    <div>节点属性</div>
    <pre>{{ selected ? JSON.stringify(selected, null, 2) : '请选择节点' }}</pre>
  </el-card>
</template>
<script setup lang="ts">
defineProps<{ selected: any }>()
</script>
