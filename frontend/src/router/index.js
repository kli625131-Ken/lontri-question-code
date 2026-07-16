import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: {
      title: '登录',
      subtitle: '使用已分配账号进入问题库运维平台'
    }
  },
  {
    path: '/',
    component: () => import('@/components/Layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: {
          title: '仪表盘首页',
          subtitle: '查看问题规模、待办预警和负责人处理负载',
          requiresAuth: true
        }
      },
      {
        path: 'issues',
        name: 'Issues',
        component: () => import('@/views/IssueWorkbench.vue'),
        meta: {
          title: '问题台账',
          subtitle: '按子项目、状态、处理人和日期筛选全部运维问题',
          requiresAuth: true
        }
      },
      {
        path: 'issues/:id',
        name: 'IssueDetail',
        component: () => import('@/views/IssueDetail.vue'),
        meta: {
          title: '问题详情',
          subtitle: '查看问题定义、处置过程、时间线和相似历史问题',
          requiresAuth: true
        }
      },
      {
        path: 'imports',
        name: 'Imports',
        component: () => import('@/views/ImportCenter.vue'),
        meta: {
          title: '数据导入',
          subtitle: '上传 Excel、校正清洗结果并提交入库',
          requiresAuth: true
        }
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/ProjectCenter.vue'),
        meta: {
          title: '项目中心',
          subtitle: '按客户和子项目查看档案、联系人和质保信息',
          requiresAuth: true
        }
      },
      {
        path: 'maintenance',
        name: 'Maintenance',
        component: () => import('@/views/MaintenanceCenter.vue'),
        meta: {
          title: '运维管理',
          subtitle: '管理运维前安排、现场记录、报价和报告输出',
          requiresAuth: true
        }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: {
          title: '知识库',
          subtitle: '检索和维护已关闭问题沉淀的标准案例',
          requiresAuth: true
        }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManagement.vue'),
        meta: {
          title: '用户管理',
          subtitle: '维护账号、角色、状态和项目授权',
          requiresAuth: true,
          requiresAdmin: true
        }
      },
      {
        path: 'admin/roles',
        name: 'AdminRoles',
        component: () => import('@/views/admin/RoleManagement.vue'),
        meta: {
          title: '角色说明',
          subtitle: '维护基础角色说明，项目范围在项目授权中配置',
          requiresAuth: true,
          requiresAdmin: true
        }
      },
      {
        path: 'admin/project-auth',
        name: 'AdminProjectAuth',
        component: () => import('@/views/admin/ProjectAuthorization.vue'),
        meta: {
          title: '项目授权',
          subtitle: '为普通用户绑定可访问项目',
          requiresAuth: true,
          requiresAdmin: true
        }
      },
      {
        path: 'admin/temp-users',
        name: 'AdminTempUsers',
        component: () => import('@/views/admin/TempAccountManagement.vue'),
        meta: {
          title: '临时账号管理',
          subtitle: '维护有效期、项目和临时访问范围',
          requiresAuth: true,
          requiresAdmin: true
        }
      }
    ]
  },
  {
    path: '/401',
    name: 'Unauthorized',
    component: () => import('@/views/401.vue'),
    meta: {
      title: '无权访问',
      subtitle: '当前账号未获得该资源的访问授权'
    }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})
