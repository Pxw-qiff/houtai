import { createRouter, createWebHistory } from 'vue-router'
import request from '../utils/request.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue'),
    meta: { title: '用户登录', requiresAuth: false }
  },
  {
    path: '/pay-test',
    redirect: '/recharge/pay-test'
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/credit/balance',
    children: [
      {
        path: 'credit/balance',
        name: 'CreditBalance',
        component: () => import('../views/credit/Balance.vue'),
        meta: { title: '积分余额' }
      },
      {
        path: 'credit/flows',
        name: 'CreditFlows',
        component: () => import('../views/credit/Flows.vue'),
        meta: { title: '积分流水' }
      },
      {
        path: 'credit/adjust',
        name: 'CreditAdjust',
        component: () => import('../views/credit/Adjust.vue'),
        meta: { title: '积分调账' }
      },
      {
        path: 'recharge/manage',
        name: 'RechargeManage',
        component: () => import('../views/recharge/Recharge.vue'),
        meta: { title: '充值管理' }
      },
      {
        path: 'recharge/pay-test',
        name: 'RechargePayTest',
        component: () => import('../views/recharge/PayTest.vue'),
        meta: { title: '支付测试' }
      },
      {
        path: 'recharge/pay-success',
        name: 'RechargePaySuccess',
        component: () => import('../views/recharge/PaySuccess.vue'),
        meta: { title: '支付成功' }
      },
      {
        path: 'task/manage',
        name: 'TaskManage',
        component: () => import('../views/task/Tasks.vue'),
        meta: { title: '任务管理' }
      },
      {
        path: 'gateway/console',
        name: 'GatewayConsole',
        component: () => import('../views/gateway/NewApiConsole.vue'),
        meta: { title: 'AI 网关控制台' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory('/credit/'),
  routes
})

router.beforeEach(async (to) => {
  // 登录页不需要鉴权
  if (to.path === '/login') {
    return true
  }

  // 检查是否有登录态
  const token = window.localStorage.getItem('chuamgwei_token')
  if (!token) {
    // 如果有 ticket，尝试用 ticket 换 token
    const ticket = to.query.ticket
    if (ticket) {
      try {
        const auth = await request.post('/v1/auth/ticket', { ticket })
        window.localStorage.setItem('chuamgwei_token', auth.token)
        window.localStorage.setItem('chuamgwei_user_uuid', auth.userUuid)
        window.localStorage.setItem('chuamgwei_username', auth.username || '')

        const query = { ...to.query }
        delete query.ticket
        return {
          path: to.path,
          query,
          replace: true
        }
      } catch (error) {
        console.error('ticket 换取 token 失败:', error)
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }

    // 没有 token 也没有 ticket，跳转登录页
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router