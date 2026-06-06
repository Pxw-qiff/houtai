<template>
  <div class="page-container">
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>充值支付配置</span>
          <el-button type="primary" link @click="openSettingsDialog">修改</el-button>
        </div>
      </template>

      <div class="settings-list">
        <div class="setting-row">
          <div class="setting-main">
            <span class="setting-label">充值兑换比例</span>
            <span class="setting-value">1 元 = {{ formatRatio(settings.ratio) }} 积分</span>
          </div>
          <div class="setting-desc">创建充值订单时固化该比例，后续修改不影响旧订单。</div>
        </div>

        <div class="setting-row">
          <div class="setting-main">
            <span class="setting-label">支付超时时间</span>
            <span class="setting-value">{{ settings.payTimeoutMinutes }} 分钟</span>
          </div>
          <div class="setting-desc">支付宝二维码有效期和本地订单支付过期时间使用同一个配置。</div>
        </div>

        <div class="setting-row">
          <div class="setting-main">
            <span class="setting-label">支付宝支付名称</span>
            <span class="setting-value">{{ settings.paySubjectPrefix }}</span>
          </div>
          <div class="setting-desc">用户扫码支付时看到的账单名称，订单号只用于系统对账和回调查单。</div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="showSettingsDialog" title="修改充值支付配置" width="460px">
      <el-form :model="settingsForm" label-width="130px">
        <el-form-item label="兑换比例（1元=）">
          <el-input-number
            v-model="settingsForm.ratio"
            :min="0.000001"
            :precision="6"
            :step="0.1"
            style="width: 100%"
          />
          <span class="form-unit">积分</span>
        </el-form-item>

        <el-form-item label="支付超时时间">
          <el-input-number
            v-model="settingsForm.payTimeoutMinutes"
            :min="1"
            :max="1440"
            :precision="0"
            :step="1"
            style="width: 100%"
          />
          <span class="form-unit">分钟</span>
        </el-form-item>

        <el-form-item label="支付宝支付名称">
          <el-input
            v-model="settingsForm.paySubjectPrefix"
            maxlength="80"
            show-word-limit
            placeholder="例如：创维AI充值"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showSettingsDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateSettings" :loading="updatingSettings">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request.js'
import { formatRatio } from '../../utils/decimal.js'

const settings = reactive({
  ratio: 0,
  payTimeoutMinutes: 30,
  paySubjectPrefix: '创维AI充值'
})
const settingsForm = reactive({
  ratio: 500000,
  payTimeoutMinutes: 30,
  paySubjectPrefix: '创维AI充值'
})
const showSettingsDialog = ref(false)
const updatingSettings = ref(false)

/** 查询当前充值支付配置 */
const fetchSettings = async () => {
  try {
    const res = await request.get('/v1/recharge/settings')
    settings.ratio = res.ratio
    settings.payTimeoutMinutes = res.payTimeoutMinutes
    settings.paySubjectPrefix = res.paySubjectPrefix
  } catch (e) {
    console.error('查询充值支付配置失败', e)
  }
}

/** 打开配置修改弹窗 */
const openSettingsDialog = () => {
  settingsForm.ratio = settings.ratio
  settingsForm.payTimeoutMinutes = settings.payTimeoutMinutes
  settingsForm.paySubjectPrefix = settings.paySubjectPrefix
  showSettingsDialog.value = true
}

/** 校验充值支付配置 */
const validateSettings = () => {
  if (!settingsForm.ratio || Number(settingsForm.ratio) < 0.000001) {
    ElMessage.warning('兑换比例不能小于0.000001')
    return false
  }
  if (!Number.isInteger(Number(settingsForm.payTimeoutMinutes)) || settingsForm.payTimeoutMinutes < 1 || settingsForm.payTimeoutMinutes > 1440) {
    ElMessage.warning('支付超时时间必须在1到1440分钟之间')
    return false
  }
  if (!settingsForm.paySubjectPrefix || !settingsForm.paySubjectPrefix.trim()) {
    ElMessage.warning('支付宝支付名称不能为空')
    return false
  }
  if (settingsForm.paySubjectPrefix.trim().length > 80) {
    ElMessage.warning('支付宝支付名称不能超过80个字符')
    return false
  }
  return true
}

/** 修改充值支付配置 */
const handleUpdateSettings = async () => {
  if (!validateSettings()) {
    return
  }
  updatingSettings.value = true
  try {
    await request.post('/v1/admin/recharge/settings', {
      ratio: settingsForm.ratio,
      payTimeoutMinutes: settingsForm.payTimeoutMinutes,
      paySubjectPrefix: settingsForm.paySubjectPrefix.trim()
    })
    showSettingsDialog.value = false
    ElMessage.success('充值支付配置修改成功')
    await fetchSettings()
  } finally {
    updatingSettings.value = false
  }
}

onMounted(fetchSettings)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.settings-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.setting-row {
  padding-bottom: 18px;
  border-bottom: 1px solid #f0f0f0;
}
.setting-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}
.setting-main {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.setting-label {
  min-width: 112px;
  color: #666;
  font-size: 14px;
}
.setting-value {
  font-weight: 700;
  color: #409eff;
  font-size: 18px;
}
.setting-desc {
  color: #999;
  font-size: 13px;
  line-height: 1.6;
  padding-left: 124px;
}
.form-unit {
  margin-left: 8px;
  color: #999;
  white-space: nowrap;
}
</style>