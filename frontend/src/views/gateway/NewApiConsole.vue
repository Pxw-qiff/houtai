<template>
  <div class="newapi-console">
    <iframe
      ref="iframeRef"
      :src="newApiUrl"
      class="newapi-iframe"
      @load="onIframeLoad"
      @error="onIframeError"
    />
    <div v-if="loading" class="loading-container">
      <div class="loading-text">正在加载 AI 网关控制台...</div>
    </div>
    <div v-if="error" class="error-container">
      <div class="error-text">{{ error }}</div>
      <el-button type="primary" @click="reload">重新加载</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const iframeRef = ref(null)
const loading = ref(true)
const error = ref('')
let loadingTimer = null

const newApiUrl = computed(() => {
  return import.meta.env.VITE_NEW_API_WEB_URL || 'http://localhost:3000'
})

const clearLoadingTimeout = () => {
  if (loadingTimer) {
    clearTimeout(loadingTimer)
    loadingTimer = null
  }
}

const startLoadingTimeout = () => {
  clearLoadingTimeout()
  loadingTimer = setTimeout(() => {
    if (loading.value) {
      loading.value = false
      error.value = '加载超时，请检查 new-api 服务地址配置'
    }
  }, 15000)
}

const onIframeLoad = () => {
  clearLoadingTimeout()
  loading.value = false
  error.value = ''
}

const onIframeError = () => {
  clearLoadingTimeout()
  loading.value = false
  error.value = '无法加载 AI 网关控制台，请检查服务是否正常运行'
}

const reload = () => {
  error.value = ''
  loading.value = true
  startLoadingTimeout()
  if (iframeRef.value) {
    iframeRef.value.src = newApiUrl.value
  }
}

onMounted(() => {
  startLoadingTimeout()
})

onBeforeUnmount(() => {
  clearLoadingTimeout()
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
  position: absolute;
  inset: 0;
  z-index: 1;
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
  position: absolute;
  inset: 0;
  z-index: 2;
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