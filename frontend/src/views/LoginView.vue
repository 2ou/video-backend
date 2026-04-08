<template>
  <div style="max-width: 360px; margin: 120px auto">
    <el-card>
      <h3>登录</h3>
      <el-form @submit.prevent>
        <el-input v-model="username" placeholder="username" />
        <el-input v-model="password" placeholder="password" show-password style="margin-top: 12px" />
        <el-button type="primary" style="margin-top: 12px" @click="onLogin">登录</el-button>
      </el-form>
      <p>默认：admin / 123456</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">

const username = ref('admin')
const password = ref('123456')
const userStore = useUserStore()
const router = useRouter()

const onLogin = async () => {
  await userStore.login(username.value, password.value)
  router.push('/projects')
}
</script>
