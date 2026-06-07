<template>
  <div class="newapi-console">
    <div v-if="loading" class="loading-container">
      <div class="loading-text">正在加载 AI 网关控制台...</div>
    </div>
    <div v-else-if="error" class="error-container">
      <div class="error-text">{{ error }}</div>
      <el-button type="primary" @click="reload">重新加载</el-button>
    </div>
    <iframe
      v-else
      ref="iframeRef"
      :src="newApiUrl"
      class="newapi-iframe"
      @load="onIframeLoad"
      @error="onIframeError"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const iframeRef = ref(null)
const loading = ref(true)
const error = ref('')

const newApiUrl = computed(() => {
  return import.meta.env.VITE_NEW_API_WEB_URL || 'http://localhost:3000'
})

const onIframeLoad = () => {
  loading.value = false
}

const onIframeError = () => {
  loading.value = false
  error.value = '无法加载 AI 网关控制台，请检查服务是否正常运行'
}

const reload = () => {
  error.value = ''
  loading.value = true
  if (iframeRef.value) {
    iframeRef.value.src = newApiUrl.value
  }
}

onMounted(() => {
  setTimeout(() => {
    if (loading.value) {
      loading.value = false
      error.value = '加载超时，请检查 new-api 服务地址配置'
    }
  }, 15000)
})
</script>

<style scoped>
.newapi-console {
  width: 100%;
  height: calc(100vh - 112px);
  position: relative;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}

.newapi-iframe {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}

.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #f5f7fa;
}

.loading-text {
  color: #606266;
  font-size: 14px;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #f5f7fa;
  gap: 16px;
}

.error-text {
  color: #f56c6c;
  font-size: 14px;
}
</style>