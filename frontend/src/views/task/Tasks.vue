<template>
  <div class="page-container">
    <!-- 提交生视频任务 -->
    <el-card class="page-card">
      <template #header><span>提交视频生成任务</span></template>
      <el-form ref="videoFormRef" :model="videoForm" :rules="videoRules" label-width="110px" style="max-width: 600px">
        <el-form-item label="用户UUID" prop="userUuid">
          <el-input v-model="videoForm.userUuid" placeholder="输入用户UUID" />
        </el-form-item>
        <el-form-item label="模型" prop="model">
          <el-select v-model="videoForm.model" placeholder="选择模型" style="width: 100%">
            <el-option label="Kling v1.5" value="kling-v1-5" />
            <el-option label="Kling v1.0" value="kling-v1-0" />
            <el-option label="Sora" value="sora" />
          </el-select>
        </el-form-item>
        <el-form-item label="正向提示词" prop="prompt">
          <el-input v-model="videoForm.prompt" type="textarea" :rows="3" placeholder="描述要生成的视频内容" />
        </el-form-item>
        <el-form-item label="负向提示词">
          <el-input v-model="videoForm.negativePrompt" placeholder="不希望出现的内容" />
        </el-form-item>
        <el-form-item label="画面比例">
          <el-select v-model="videoForm.aspectRatio" placeholder="默认16:9" clearable style="width: 160px">
            <el-option label="16:9" value="16:9" />
            <el-option label="9:16" value="9:16" />
            <el-option label="1:1" value="1:1" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长">
          <el-select v-model="videoForm.duration" placeholder="默认5s" clearable style="width: 160px">
            <el-option label="5秒" value="5s" />
            <el-option label="10秒" value="10s" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmitVideo" :loading="submittingVideo">提交任务</el-button>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="lastVideoTask" title="视频任务结果" :column="2" border style="margin-top: 16px">
        <el-descriptions-item label="任务ID">{{ lastVideoTask.taskId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ lastVideoTask.status }}</el-descriptions-item>
        <el-descriptions-item label="用户UUID">{{ lastVideoTask.userUuid }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ lastVideoTask.model }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 提交生图任务 -->
    <el-card class="page-card" style="margin-top: 16px">
      <template #header><span>提交图片生成任务</span></template>
      <el-form ref="imageFormRef" :model="imageForm" :rules="imageRules" label-width="110px" style="max-width: 600px">
        <el-form-item label="用户UUID" prop="userUuid">
          <el-input v-model="imageForm.userUuid" placeholder="输入用户UUID" />
        </el-form-item>
        <el-form-item label="模型" prop="model">
          <el-select v-model="imageForm.model" placeholder="选择模型" style="width: 100%">
            <el-option label="Flux" value="flux" />
            <el-option label="Midjourney" value="midjourney" />
            <el-option label="Stable Diffusion" value="sd" />
            <el-option label="DALL-E 3" value="dalle3" />
          </el-select>
        </el-form-item>
        <el-form-item label="正向提示词" prop="prompt">
          <el-input v-model="imageForm.prompt" type="textarea" :rows="3" placeholder="描述要生成的图片内容" />
        </el-form-item>
        <el-form-item label="负向提示词">
          <el-input v-model="imageForm.negativePrompt" placeholder="不希望出现的内容" />
        </el-form-item>
        <el-form-item label="图片尺寸">
          <el-input v-model="imageForm.size" placeholder="如 1024x1024" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmitImage" :loading="submittingImage">提交任务</el-button>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="lastImageTask" title="图片任务结果" :column="2" border style="margin-top: 16px">
        <el-descriptions-item label="任务ID">{{ lastImageTask.taskId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ lastImageTask.status }}</el-descriptions-item>
        <el-descriptions-item label="用户UUID">{{ lastImageTask.userUuid }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ lastImageTask.model }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request.js'

// ======== 视频任务 ========
const videoFormRef = ref(null)
const submittingVideo = ref(false)
const lastVideoTask = ref(null)

const videoForm = reactive({
  userUuid: '',
  model: 'kling-v1-5',
  prompt: '',
  negativePrompt: '',
  aspectRatio: '',
  duration: ''
})

const videoRules = {
  userUuid: [{ required: true, message: '请输入用户UUID', trigger: 'blur' }],
  model: [{ required: true, message: '请选择模型', trigger: 'change' }],
  prompt: [{ required: true, message: '请输入提示词', trigger: 'blur' }]
}

const handleSubmitVideo = async () => {
  const valid = await videoFormRef.value.validate().catch(() => false)
  if (!valid) return
  submittingVideo.value = true
  try {
    const body = { ...videoForm }
    if (!body.negativePrompt) delete body.negativePrompt
    if (!body.aspectRatio) delete body.aspectRatio
    if (!body.duration) delete body.duration
    const res = await request.post('/v1/video/generations', body)
    lastVideoTask.value = res.data
    ElMessage.success(`视频任务提交成功，任务ID：${res.data.taskId}`)
  } finally {
    submittingVideo.value = false
  }
}

// ======== 图片任务 ========
const imageFormRef = ref(null)
const submittingImage = ref(false)
const lastImageTask = ref(null)

const imageForm = reactive({
  userUuid: '',
  model: 'flux',
  prompt: '',
  negativePrompt: '',
  size: ''
})

const imageRules = {
  userUuid: [{ required: true, message: '请输入用户UUID', trigger: 'blur' }],
  model: [{ required: true, message: '请选择模型', trigger: 'change' }],
  prompt: [{ required: true, message: '请输入提示词', trigger: 'blur' }]
}

const handleSubmitImage = async () => {
  const valid = await imageFormRef.value.validate().catch(() => false)
  if (!valid) return
  submittingImage.value = true
  try {
    const body = { ...imageForm }
    if (!body.negativePrompt) delete body.negativePrompt
    if (!body.size) delete body.size
    const res = await request.post('/v1/images/generations', body)
    lastImageTask.value = res.data
    ElMessage.success(`图片任务提交成功，任务ID：${res.data.taskId}`)
  } finally {
    submittingImage.value = false
  }
}
</script>

<style scoped>
.page-card + .page-card {
  margin-top: 16px;
}
</style>