<template>
  <div class="admin-layout">
    <el-container class="layout-container">
      <!-- 侧边栏 -->
      <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
        <div class="aside-header">
          <span v-show="!isCollapse" class="aside-title">后台管理</span>
          <span v-show="isCollapse" class="aside-title--mini">后</span>
        </div>

        <el-scrollbar>
          <el-menu
            :default-active="activeMenu"
            :default-openeds="defaultOpeneds"
            :collapse="isCollapse"
            :collapse-transition="false"
            router
            background-color="#1f2d3d"
            text-color="#bfcbd9"
            active-text-color="#409eff"
          >
            <el-sub-menu index="credit">
              <template #title>
                <span class="menu-icon">◆</span>
                <span>积分管理</span>
              </template>
              <el-menu-item index="/credit/balance">
                <span class="menu-icon--sub">○</span>
                <span>积分余额</span>
              </el-menu-item>
              <el-menu-item index="/credit/flows">
                <span class="menu-icon--sub">○</span>
                <span>积分流水</span>
              </el-menu-item>
              <el-menu-item index="/credit/adjust">
                <span class="menu-icon--sub">○</span>
                <span>积分调账</span>
              </el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="recharge">
              <template #title>
                <span class="menu-icon">◆</span>
                <span>充值管理</span>
              </template>
              <el-menu-item index="/recharge/manage">
                <span class="menu-icon--sub">○</span>
                <span>充值管理</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item index="/gateway/console">
              <span class="menu-icon">◆</span>
              <span>AI 网关控制台</span>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
      </el-aside>

      <!-- 右侧主体 -->
      <el-container>
        <el-header class="layout-header">
          <div class="header-left" @click="toggleCollapse">
            <span class="collapse-icon">{{ isCollapse ? '☰' : '✕' }}</span>
          </div>
          <div class="header-right">
            <span class="header-avatar">{{ userInitial }}</span>
            <el-dropdown @command="handleCommand">
              <span class="header-name">{{ username }}</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <el-main class="layout-main">
          <div class="main-breadcrumb">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item>{{ parentMenu }}</el-breadcrumb-item>
              <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

const activeMenu = computed(() => route.path)
const defaultOpeneds = ['credit', 'recharge']

const username = computed(() => {
  return window.localStorage.getItem('chuamgwei_username') || '用户'
})

const userInitial = computed(() => {
  const name = username.value
  return name ? name.charAt(0).toUpperCase() : 'U'
})

const parentMenu = computed(() => {
  const menuMap = {
    credit: '积分管理',
    recharge: '充值管理',
    task: '任务管理',
    gateway: 'AI 网关'
  }
  const seg = route.path.split('/')[1]
  return menuMap[seg] || ''
})

const pageTitle = computed(() => route.meta?.title || '')

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })

      // 清除登录态
      window.localStorage.removeItem('chuamgwei_token')
      window.localStorage.removeItem('chuamgwei_user_uuid')
      window.localStorage.removeItem('chuamgwei_username')

      // 跳转登录页
      router.push('/login')
    } catch {
      // 用户取消
    }
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  background: #f0f2f5;
}

.layout-container {
  height: 100%;
}

/* ======== 侧边栏 ======== */
.layout-aside {
  background: #1f2d3d;
  overflow: hidden;
  transition: width 0.28s;
}

.aside-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1a2736;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  user-select: none;
}

.aside-title--mini {
  font-size: 20px;
  font-weight: 700;
}

.el-menu {
  border-right: none;
}

.el-menu-item.is-active {
  background-color: #1890ff1a !important;
}

.menu-icon {
  margin-right: 8px;
  font-size: 10px;
  color: #409eff;
}

.menu-icon--sub {
  margin-right: 8px;
  font-size: 6px;
  color: #bfcbd9;
}

/* ======== 顶栏 ======== */
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 20px;
  height: 56px;
}

.collapse-icon {
  font-size: 18px;
  cursor: pointer;
  color: #666;
  user-select: none;
  transition: color 0.2s;
}

.collapse-icon:hover {
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.header-name {
  color: #333;
  font-size: 14px;
  cursor: pointer;
}

.header-name:hover {
  color: #409eff;
}

/* ======== 主内容区 ======== */
.layout-main {
  background: #f0f2f5;
  padding: 0 20px 20px;
  overflow-y: auto;
}

.main-breadcrumb {
  padding: 16px 0;
}
</style>