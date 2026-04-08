<template>
  <div style="padding:16px">
    <el-card>
      <template #header>
        <div style="display:flex; justify-content:space-between">
          <span>运行详情 #{{ runStore.run?.id }}</span>
          <el-button @click="refresh">刷新</el-button>
        </div>
      </template>
      <p>状态：{{ runStore.run?.status }}</p>
      <p>错误：{{ runStore.run?.error_message }}</p>
      <pre>{{ runStore.run?.output_json }}</pre>

      <el-table :data="runStore.nodes" style="margin-top:12px">
        <el-table-column prop="node_id" label="Node ID" />
        <el-table-column prop="node_type" label="类型" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="provider_task_id" label="ProviderTaskID" />
        <el-table-column label="结果 URL">
          <template #default="scope">
            <div v-if="scope.row.output_json?.result_urls">
              <a v-for="u in scope.row.output_json.result_urls" :key="u" :href="u" target="_blank">{{ u }}</a>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useRunStore } from '../stores/run'

const runStore = useRunStore()
const route = useRoute()
const runId = Number(route.params.id)

const refresh = () => runStore.loadRun(runId)
onMounted(refresh)
</script>
