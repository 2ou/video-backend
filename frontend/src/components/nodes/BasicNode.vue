<template>
  <div style="padding:6px 10px; border:1px solid #999; border-radius:6px; background:#fff">{{ data.label || type }}</div>
</template>
<script setup lang="ts">
defineProps<{ data: any; type: string }>()
</script>
