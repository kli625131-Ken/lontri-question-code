import router from './index'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'

const appTitle = '问题库运维平台'
const whiteList = ['/login']

router.beforeEach(async (to, from, next) => {
  const token = getToken()
  const userStore = useUserStore()

  if (token) {
    if (to.path === '/login') {
      next('/dashboard')
      return
    }
    if (!userStore.userInfo) {
      try {
        await userStore.fetchUserInfo()
      } catch (error) {
        next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
        return
      }
    }
    if (to.meta?.requiresAdmin && !userStore.isAdmin) {
      next('/401')
      return
    }
    next()
    return
  }

  if (whiteList.includes(to.path)) {
    next()
    return
  }

  next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
})

router.afterEach(to => {
  document.title = to.meta?.title ? `${to.meta.title} | ${appTitle}` : appTitle
})
