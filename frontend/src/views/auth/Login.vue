<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span>用户登录</span>
        </div>
      </template>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="80px"
        class="login-form"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="logging"
            @click="handleLogin"
            style="width: 100%"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-tips">
        <p>测试账号：admin</p>
        <p>测试密码：admin123</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const loginFormRef = ref(null)
const logging = ref(false)

const loginForm = ref({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

// 生成设备标识
const generateMachineCode = () => {
  let machineCode = localStorage.getItem('chuamgwei_machine_code')
  if (!machineCode) {
    machineCode = `web_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
    localStorage.setItem('chuamgwei_machine_code', machineCode)
  }
  return machineCode
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }

  logging.value = true

  try {
    const machineCode = generateMachineCode()

    const response = await axios.post('/ai-anime/user/login/login', {
      loginField: loginForm.value.username,
      password: loginForm.value.password,
      loginMode: 'username',
      machineCode
    })

    if (response.data.success && response.data.data) {
      const { token, user } = response.data.data

      // 保存登录态
      localStorage.setItem('chuamgwei_token', token)
      localStorage.setItem('chuamgwei_user_uuid', user.user_uuid)
      localStorage.setItem('chuamgwei_username', user.username)

      ElMessage.success('登录成功')

      // 跳转到首页
      router.push('/credit/balance')
    } else {
      ElMessage.error(response.data.error || '登录失败')
    }
  } catch (error) {
    console.error('登录失败:', error)
    const errorMsg = error.response?.data?.error || error.message || '登录失败，请稍后重试'
    ElMessage.error(errorMsg)
  } finally {
    logging.value = false
  }
}

// 检查是否已登录
onMounted(() => {
  const token = localStorage.getItem('chuamgwei_token')
  if (token) {
    router.push('/credit/balance')
  }
})
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.login-form {
  padding: 20px 0;
}

.login-tips {
  margin-top: 20px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
  text-align: center;
  color: #909399;
  font-size: 13px;
  line-height: 1.8;
}

.login-tips p {
  margin: 0;
}
</style>