<template>
  <div class="page-container">
    <el-card class="page-card">
      <template #header>积分调账</template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        style="max-width: 600px"
      >
        <el-form-item label="目标用户UUID" prop="userUuid">
          <el-input v-model="form.userUuid" placeholder="输入要调账的用户UUID" />
        </el-form-item>
        <el-form-item label="调账方向" prop="adjustType">
          <el-radio-group v-model="form.adjustType">
            <el-radio :value="1">增加积分</el-radio>
            <el-radio :value="2">扣减积分</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="积分数量" prop="points">
          <el-input-number
            v-model="form.points"
            :min="0.000001"
            :precision="6"
            :step="0.1"
            :max="99999999"
            placeholder="积分数量"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="调账原因" prop="reason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入调账原因"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">提交调账</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request.js'

const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  userUuid: '',
  adjustType: 1,
  points: null,
  reason: ''
})

const rules = {
  userUuid: [{ required: true, message: '请输入目标用户UUID', trigger: 'blur' }],
  adjustType: [{ required: true, message: '请选择调账方向', trigger: 'change' }],
  points: [{ required: true, message: '请输入积分数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入调账原因', trigger: 'blur' }]
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await request.post('/v1/admin/credit/adjust', { ...form })
    ElMessage.success('调账成功')
    handleReset()
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  formRef.value?.resetFields()
  form.adjustType = 1
  form.points = null
  form.reason = ''
}
</script>