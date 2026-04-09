<template>
  <div class="palette-card">
    <div class="palette-title">节点工具栏</div>
    <div class="palette-subtitle">双击画布可以自由选择节点，也可以点击下面快速添加</div>

    <button
      v-for="item in selectableItems"
      :key="item.type"
      class="palette-item"
      type="button"
      :style="{ '--accent-color': item.accent }"
      @click="$emit('add', item.type)"
    >
      <span class="palette-icon">{{ item.icon }}</span>
      <span class="palette-content">
        <span class="palette-name">{{ item.title }}</span>
        <span class="palette-desc">{{ item.subtitle }}</span>
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NODE_CATALOG, SELECTABLE_NODE_TYPES } from '../../constants/workflow'

defineEmits<{ add: [string] }>()

const selectableItems = computed(() =>
  NODE_CATALOG.filter((item) => SELECTABLE_NODE_TYPES.includes(item.type))
)
</script>

<style scoped>
.palette-card {
  border-radius: 16px;
  padding: 14px;
  background: linear-gradient(160deg, rgba(17, 21, 29, 0.95), rgba(11, 14, 22, 0.92));
  border: 1px solid rgba(102, 120, 173, 0.38);
  color: #edf1ff;
}

.palette-title {
  font-size: 14px;
  font-weight: 700;
}

.palette-subtitle {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: rgba(205, 214, 255, 0.78);
}

.palette-item {
  width: 100%;
  margin-top: 10px;
  border: 1px solid color-mix(in oklab, var(--accent-color) 38%, #334368 62%);
  border-radius: 12px;
  background: rgba(17, 23, 36, 0.92);
  color: #eef3ff;
  padding: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.palette-item:hover {
  border-color: color-mix(in oklab, var(--accent-color) 62%, #a0b5ff 38%);
  transform: translateY(-1px);
}

.palette-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  background: color-mix(in oklab, var(--accent-color) 35%, #0f1424 65%);
}

.palette-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.palette-name {
  font-size: 13px;
  font-weight: 600;
}

.palette-desc {
  margin-top: 2px;
  font-size: 11px;
  color: rgba(199, 210, 255, 0.74);
}
</style>
