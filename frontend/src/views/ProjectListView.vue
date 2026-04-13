<template>
  <div class="project-page" v-loading="loading" element-loading-background="rgba(0, 0, 0, 0.7)">
    <header class="navbar">
      <div class="brand">
        <span class="logo-icon">⚡</span>
        <span class="logo-text">AI VIDEO CANVAS</span>
      </div>
      <div class="actions">
        <el-button type="primary" size="large" @click="handleCreate" class="create-btn">
          <el-icon><Plus /></el-icon>
          <span>新建创作项目</span>
        </el-button>
      </div>
    </header>

    <main class="content-container">
      <div class="section-title">我的项目 ({{ projects.length }})</div>

      <div v-if="projects.length > 0" class="project-grid">
        <div
            v-for="item in projects"
            :key="item.id"
            class="project-card"
            @click="openProject(item.id)"
        >
          <div class="preview-area">
            <div class="overlay">
              <el-button type="primary" circle class="play-btn">
                <el-icon><Right /></el-icon>
              </el-button>
            </div>
            <div class="placeholder">
              <el-icon><Film /></el-icon>
            </div>
          </div>

          <div class="info-area">
            <div class="title-row">
              <span class="name">{{ item.name || '未命名项目' }}</span>
              <el-dropdown trigger="click" @click.stop>
                <el-icon class="more-icon"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleRename(item)">重命名</el-dropdown-item>
                    <el-dropdown-item divided @click="handleDelete(item.id)" style="color: #f56c6c">删除项目</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <div class="time-row">
              {{ formatDate(item.updatedAt) }}
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <div class="empty-box">
          <el-icon class="icon"><FolderOpened /></el-icon>
          <h3>开启你的第一个 AI 视频流</h3>
          <p>在这里，你可以像电影导演一样在画布上编排你的视频逻辑</p>
          <el-button type="primary" size="large" @click="handleCreate" plain>立即创建</el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Right, Film, MoreFilled, FolderOpened } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const projects = ref([])
const loading = ref(false)

// 获取项目列表
const fetchList = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/v1/projects')
    projects.value = res.data.data || []
  } catch (e) {
    ElMessage.error('服务连接异常')
  } finally {
    loading.value = false
  }
}

// 创建项目
const handleCreate = async () => {
  try {
    const { value: name } = await ElMessageBox.prompt('给你的项目起个名字', '新建创作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：科幻短片-镜头1',
      inputPattern: /\S+/,
      inputErrorMessage: '名字不能为空'
    })

    loading.value = true
    const res = await axios.post('/api/v1/projects', { name })
    ElMessage.success('项目已创建')
    openProject(res.data.data.id)
  } catch (e) {} finally { loading.value = false }
}

// 跳转到编辑器
const openProject = (id: number) => {
  router.push(`/canvas/${id}`)
}

// 删除项目
const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该项目吗？数据将无法找回。', '风险提示', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/api/v1/projects/${id}`)
    ElMessage.success('项目已移除')
    fetchList()
  } catch (e) {}
}

const formatDate = (date: any) => {
  if (!date) return '刚刚更新'
  const d = new Date(date)
  return `${d.getMonth() + 1}月${d.getDate()}日 · ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(fetchList)
</script>

<style scoped>
.project-page {
  min-height: 100vh;
  background-color: #0d0d0f; /* 极简黑 */
  color: #e5e5e7;
  display: flex;
  flex-direction: column;
}

/* 顶部导航：磨砂玻璃效果 */
.navbar {
  height: 64px;
  background: rgba(21, 21, 24, 0.8);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #2b2b30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.logo-icon { font-size: 24px; margin-right: 10px; }
.logo-text { font-weight: 800; letter-spacing: 2px; font-size: 18px; color: #fff; }

.content-container {
  flex: 1;
  padding: 40px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 30px;
  color: #fff;
}

/* 项目网格布局 */
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 30px;
}

/* 卡片交互设计 */
.project-card {
  background: #1a1a1d;
  border: 1px solid #2b2b30;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

/* 重点：鼠标悬浮反馈 */
.project-card:hover {
  transform: translateY(-8px);
  border-color: #3b82f6;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.6), 0 0 20px rgba(59, 130, 246, 0.2);
}

/* 重点：点击按下反馈 */
.project-card:active {
  transform: scale(0.98) translateY(-4px);
}

.preview-area {
  height: 180px;
  background: #232328;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder { font-size: 50px; color: #333; }

.overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.project-card:hover .overlay { opacity: 1; }

.info-area { padding: 20px; }
.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.name { font-size: 17px; font-weight: 600; color: #fff; }
.more-icon { color: #555; cursor: pointer; }
.more-icon:hover { color: #fff; }

.time-row { font-size: 12px; color: #82828a; }

/* 空状态 */
.empty-state {
  height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-box { text-align: center; max-width: 400px; }
.empty-box .icon { font-size: 80px; color: #232328; margin-bottom: 20px; }
.empty-box h3 { font-size: 22px; color: #fff; margin-bottom: 15px; }
.empty-box p { color: #82828a; line-height: 1.6; margin-bottom: 30px; }

/* 深度定制下拉菜单 */
:deep(.el-dropdown-menu) {
  background-color: #1a1a1d !important;
  border: 1px solid #333 !important;
}
:deep(.el-dropdown-menu__item) {
  color: #ccc !important;
}
:deep(.el-dropdown-menu__item:hover) {
  background-color: #2b2b30 !important;
  color: #fff !important;
}
</style>