<template>
  <div style="padding: 16px">
    <el-card>
      <template #header>
        <div style="display:flex; justify-content:space-between">
          <span>项目列表</span>
          <el-button @click="logout">退出</el-button>
        </div>
      </template>
      <div style="display:flex; gap:8px; margin-bottom: 12px">
        <el-input v-model="name" placeholder="项目名称" style="width:200px" />
        <el-input v-model="description" placeholder="描述" style="width:300px" />
        <el-button type="primary" @click="create">创建</el-button>
      </div>
      <el-table :data="projectStore.projects">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="描述" />
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button @click="goCanvas(scope.row.id)">编辑画布</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">


const projectStore = useProjectStore()
const userStore = useUserStore()
const router = useRouter()
const name = ref('')
const description = ref('')

onMounted(() => projectStore.fetchProjects())

const create = async () => {
  await projectStore.createProject(name.value, description.value)
  name.value = ''
  description.value = ''
}

const goCanvas = (id: number) => router.push(`/projects/${id}/canvas`)
const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>
