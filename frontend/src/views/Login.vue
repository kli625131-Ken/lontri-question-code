<template>
  <div class="login-page">
    <section class="login-brand">
      <div class="brand-top">
        <span class="brand-mark" aria-hidden="true">
          <i></i>
          <i></i>
          <i></i>
        </span>
        <span>问题库运维平台</span>
      </div>

      <div class="brand-main">
        <div class="brand-copy">
          <h1>高效运维 · <strong>智能驱动</strong></h1>
          <p>统一管理问题数据，提升处理效率，保障业务稳定运行</p>
        </div>

        <img class="brand-visual" :src="loginImage" alt="问题库运维平台能力图" />
      </div>

      <div class="feature-row">
        <div v-for="item in features" :key="item.title" class="feature-item">
          <el-icon><component :is="item.icon" /></el-icon>
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.desc }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="login-side">
      <div class="login-card">
        <div class="login-title">
          <h2>欢迎登录</h2>
          <p>问题库运维平台</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入账号" prefix-icon="User" size="large" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              show-password
              size="large"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="row-actions">
            <el-checkbox v-model="rememberMe">记住登录状态</el-checkbox>
            <button type="button" class="text-link">忘记密码?</button>
          </div>

          <el-button type="primary" size="large" class="login-button" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form>

        <div class="dev-hint">
          <el-icon><InfoFilled /></el-icon>
          <span>开发环境默认账号：</span>
          <strong class="mono">admin / admin123</strong>
        </div>
      </div>

      <footer class="page-footer">
        <div class="footer-links">
          <span>技术支持：support@wtk.com</span>
          <span>使用手册</span>
          <span>隐私政策</span>
        </div>
        <p>© 2026 问题库运维平台 版权所有</p>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import loginImage from '@/img/login.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const rememberMe = ref(true)

const features = [
  { title: '集中管理', desc: '统一问题库管理', icon: 'Collection' },
  { title: '高效处理', desc: '流程自动化流转', icon: 'Timer' },
  { title: '数据洞察', desc: '多维数据分析', icon: 'TrendCharts' },
  { title: '安全可靠', desc: '权限与数据安全', icon: 'Lock' }
]

const form = reactive({
  username: 'admin',
  password: 'admin123'
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login(form)
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data.userInfo)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 47.5%) minmax(0, 52.5%);
  background: linear-gradient(102deg, #f8fbff 0%, #eef6ff 45%, #fbfdff 100%);
}

.login-brand,
.login-side {
  min-width: 0;
  position: relative;
}

.login-brand {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  padding: 48px 66px 70px 46px;
  overflow: hidden;
}

.login-brand::after {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 45% 52%, rgba(22, 119, 255, 0.10), transparent 30%),
    radial-gradient(circle at 13% 83%, rgba(24, 168, 113, 0.08), transparent 24%);
  pointer-events: none;
}

.brand-top,
.brand-main,
.feature-row {
  position: relative;
  z-index: 1;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 29px;
  font-weight: 900;
  letter-spacing: 0;
  color: #071b3d;
}

.brand-mark {
  position: relative;
  width: 44px;
  height: 38px;
  display: inline-block;
}

.brand-mark i {
  position: absolute;
  left: 5px;
  width: 34px;
  height: 20px;
  border-radius: 5px;
  transform: rotate(28deg) skewX(-30deg);
  background: #1677ff;
}

.brand-mark i::after {
  content: "";
  position: absolute;
  inset: 6px;
  background: #f8fbff;
  border-radius: 3px;
}

.brand-mark i:nth-child(1) {
  top: 0;
}

.brand-mark i:nth-child(2) {
  top: 11px;
  background: #0f62d8;
}

.brand-mark i:nth-child(3) {
  top: 22px;
  background: #18b879;
}

.brand-main {
  align-self: center;
  padding-top: 38px;
  padding-left: 24px;
}

.brand-copy h1 {
  margin: 0;
  font-size: clamp(38px, 2.7vw, 48px);
  line-height: 1.18;
  color: #061938;
  font-weight: 900;
}

.brand-copy h1 strong {
  color: var(--primary);
}

.brand-copy p {
  margin: 22px 0 0;
  color: #4c5d78;
  font-size: clamp(18px, 1.16vw, 20px);
  line-height: 1.7;
}

.brand-visual {
  display: block;
  width: min(100%, 704px);
  margin: 52px 0 0 0;
  object-fit: contain;
  filter: drop-shadow(0 26px 48px rgba(22, 119, 255, 0.10));
}

.feature-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-left: 24px;
}

.feature-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  color: #0f1f3d;
}

.feature-item .el-icon {
  color: var(--primary);
  font-size: 21px;
  margin-top: 2px;
}

.feature-item strong,
.feature-item span {
  display: block;
}

.feature-item strong {
  font-size: 16px;
  font-weight: 900;
}

.feature-item span {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.45;
}

.login-side {
  display: grid;
  place-items: center;
  padding: 52px 24px 104px;
  background: rgba(255, 255, 255, 0.56);
  box-shadow: inset 1px 0 0 rgba(203, 219, 240, 0.45);
}

.login-card {
  width: min(100%, 578px);
  padding: 58px 36px 66px;
  border: 1px solid rgba(218, 228, 242, 0.92);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 28px 76px rgba(16, 38, 76, 0.10);
}

.login-title {
  text-align: center;
  margin-bottom: 38px;
}

.login-title h2 {
  margin: 0;
  font-size: 31px;
  line-height: 1.2;
  font-weight: 900;
  color: #061938;
}

.login-title p {
  margin: 13px 0 0;
  color: var(--text-muted);
  font-size: 20px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 38px;
}

.login-form :deep(.el-form-item__label) {
  padding-bottom: 9px;
  font-size: 16px;
  font-weight: 800;
  color: #1f2f4a;
}

.login-form :deep(.el-form-item__label::before) {
  display: none;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 56px;
  padding: 0 16px;
  border-color: #cfdbea;
}

.login-form :deep(.el-input__inner) {
  font-size: 18px;
  color: #111827;
}

.login-form :deep(.el-form-item.is-error .el-input__wrapper) {
  border-color: var(--danger);
}

.login-form :deep(.el-form-item__error) {
  padding-top: 8px;
  color: var(--danger);
  font-size: 14px;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 2px 0 31px;
}

.row-actions :deep(.el-checkbox__label) {
  color: #1f2f4a;
  font-size: 15px;
}

.text-link {
  border: none;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
  font-size: 15px;
}

.login-button {
  width: 100%;
  height: 58px;
  border-radius: 8px;
  font-size: 18px;
  font-weight: 900;
  box-shadow: 0 12px 24px rgba(22, 119, 255, 0.24);
}

.dev-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 28px;
  min-height: 51px;
  padding: 12px 14px;
  border: 1px solid #b9d7ff;
  border-radius: 8px;
  color: var(--primary);
  background: #f0f7ff;
  font-size: 15px;
}

.dev-hint .el-icon {
  font-size: 17px;
}

.page-footer {
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 50px;
  color: var(--text-muted);
  text-align: center;
}

.footer-links {
  display: flex;
  justify-content: center;
  gap: 0;
  flex-wrap: wrap;
}

.footer-links span {
  padding: 0 28px;
  line-height: 1.2;
}

.footer-links span + span {
  border-left: 1px solid #b8c5d8;
}

.page-footer p {
  margin: 10px 0 0;
}

@media (min-width: 1600px) {
  .login-brand {
    padding-right: 74px;
  }

  .brand-visual {
    width: min(100%, 748px);
  }
}

@media (max-width: 1280px) {
  .login-brand {
    padding: 38px 34px 48px;
  }

  .brand-copy h1 {
    font-size: 36px;
  }

  .feature-row {
    gap: 10px;
  }
}

@media (max-width: 1080px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-brand {
    display: block;
    padding: 30px 24px 0;
  }

  .brand-main,
  .feature-row {
    display: none;
  }

  .login-side {
    min-height: calc(100vh - 82px);
    padding: 28px 20px 98px;
    box-shadow: none;
  }
}

@media (max-width: 640px) {
  .brand-top {
    font-size: 24px;
  }

  .login-card {
    padding: 36px 22px 32px;
  }

  .login-title h2 {
    font-size: 28px;
  }

  .footer-links span {
    padding: 0 12px;
  }

  .dev-hint {
    flex-wrap: wrap;
  }
}
</style>
