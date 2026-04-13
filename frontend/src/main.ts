import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'

// 引入你刚才创建的暗黑主题 CSS
import './assets/dark-theme.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')