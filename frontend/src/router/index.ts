import { createRouter, createWebHistory } from 'vue-router'

import LoginView from '../views/LoginView.vue'
import ProjectListView from '../views/ProjectListView.vue'
import CanvasEditorView from '../views/CanvasEditorView.vue'
import RunDetailView from '../views/RunDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/projects' },
    { path: '/login', component: LoginView },
    { path: '/projects', component: ProjectListView },
    { path: '/projects/:id/canvas', component: CanvasEditorView },
    { path: '/runs/:id', component: RunDetailView }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') return '/login'
  return true
})

export default router
