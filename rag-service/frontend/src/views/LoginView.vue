<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const username = ref('admin')
const password = ref('')
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

async function submit() {
  if (!username.value || !password.value) return
  try {
    await auth.signIn(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (error) {
    ElMessage.error(errorMessage(error, '登录失败'))
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story">
      <div class="login-story__grid" />
      <div class="login-story__content">
        <div class="login-logo"><div class="brand__mark"><span /><span /><span /></div>AgentTo</div>
        <p class="eyebrow eyebrow--light">RAG OBSERVABILITY</p>
        <h1>看清知识<br />如何成为答案</h1>
        <p>从文件解析、结构化分块，到混合召回与精排，所有关键步骤都有迹可循。</p>
        <div class="pipeline-strip"><span>PARSE</span><i /><span>CHUNK</span><i /><span>RECALL</span><i /><span>RERANK</span></div>
      </div>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <p class="eyebrow">TECHNICAL ADMIN</p>
        <h2>欢迎回来</h2>
        <p>登录 RAG 技术管理台</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="管理员账号">
            <el-input v-model="username" size="large" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item label="登录密码">
            <el-input v-model="password" size="large" type="password" show-password :prefix-icon="Lock"
              autocomplete="current-password" @keyup.enter="submit" />
          </el-form-item>
          <el-button class="login-button" type="primary" size="large" :loading="auth.loading" @click="submit">进入控制台</el-button>
        </el-form>
        <div class="login-note"><span /> 当前为平台自维护管理员账号</div>
      </div>
    </section>
  </div>
</template>
