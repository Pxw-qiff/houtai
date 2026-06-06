<template>
  <div class="pay-test-page">
    <el-card class="pay-test-card">
      <template #header>
        <div class="card-header">
          <span>临时 PC 端支付测试</span>
          <el-tag type="warning">测试页</el-tag>
        </div>
      </template>

      <el-alert
        title="此页面临时模拟 PC 客户端，不属于后端管理入口。正式链路会由接入项目发起订单，并接收管道端后端通知。"
        type="info"
        show-icon
        :closable="false"
        class="page-alert"
      />

      <el-form ref="orderFormRef" :model="orderForm" :rules="orderRules" label-width="110px" class="order-form">
        <el-form-item label="充值金额(元)" prop="amount">
          <el-input-number v-model="orderForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="支付方式" prop="payType">
          <el-select v-model="orderForm.payType" placeholder="选择支付方式" style="width: 100%">
            <el-option label="支付宝" value="ALIPAY" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCreateOrder" :loading="creating">创建测试订单</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="lastOrder" title="测试订单" :column="2" border class="order-result">
        <el-descriptions-item label="订单号">{{ lastOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ formatOrderStatus(lastOrder.status) }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ lastOrder.amount }} 元</el-descriptions-item>
        <el-descriptions-item label="可得积分">{{ formatPoints(lastOrder.points) }}</el-descriptions-item>
        <el-descriptions-item label="用户UUID">{{ lastOrder.userUuid }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ lastOrder.payType }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="lastOrder && canShowPayAction(lastOrder.status)" class="pay-actions">
        <el-button type="success" @click="handleShowQr" :loading="generatingQr">弹出支付宝二维码</el-button>
        <span class="action-tip">请使用支付宝沙箱钱包扫码；预下单异常可重试</span>
      </div>
    </el-card>

    <el-dialog
      v-model="showQrDialog"
      title="支付宝扫码支付"
      width="420px"
      align-center
      @closed="handleQrDialogClosed"
    >
      <div class="pay-dialog">
        <div class="pay-title">请使用支付宝沙箱钱包扫码</div>
        <img v-if="qrImageUrl" class="pay-qrcode" :src="qrImageUrl" alt="支付宝支付二维码" />
        <el-skeleton v-else :rows="4" animated />
        <div v-if="paymentQr.orderNo" class="pay-meta">订单号：{{ paymentQr.orderNo }}</div>
        <div v-if="lastOrder" class="pay-meta">金额：{{ lastOrder.amount }} 元</div>
        <div v-if="paymentQr.expireMinutes" class="pay-meta">二维码有效期：{{ paymentQr.expireMinutes }} 分钟</div>
        <el-alert
          class="pay-status"
          :title="pollStatusText"
          :type="polling ? 'warning' : 'info'"
          show-icon
          :closable="false"
        />
        <div class="pay-tip">
          当前测试页每 2 秒查询一次本项目后端订单状态；到账成功后会自动跳转成功页面。
        </div>
      </div>
      <template #footer>
        <el-button @click="showQrDialog = false">关闭并停止轮询</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import request from '../../utils/request.js'
import { formatPoints } from '../../utils/decimal.js'

const router = useRouter()
const orderFormRef = ref(null)
const creating = ref(false)
const generatingQr = ref(false)
const polling = ref(false)
const pollStatusText = ref('等待扫码支付')
const lastOrder = ref(null)
const showQrDialog = ref(false)
const qrImageUrl = ref('')
const paymentQr = reactive({
  orderNo: '',
  qrCode: '',
  expireMinutes: ''
})
let pollTimer = null

const successStatuses = ['CREDITED']
const failedStatuses = ['FAILED', 'TIMEOUT', 'CANCELLED', 'EXCEPTION', 'PRECREATE_FAILED']
const payableStatuses = ['CREATED', 'WAIT_PAY', 'PRECREATE_UNKNOWN', 'PRECREATE_FAILED']
const orderStatusLabels = {
  CREATED: '本地订单已创建',
  PRECREATING: '正在生成支付二维码',
  WAIT_PAY: '等待支付',
  PRECREATE_UNKNOWN: '预下单状态未知，可重试',
  PRECREATE_FAILED: '预下单失败，可重试',
  PAID: '已支付，待入账',
  CREDITED: '已入账',
  FAILED: '支付失败',
  TIMEOUT: '支付超时',
  CANCELLED: '已取消',
  EXCEPTION: '入账异常'
}

const orderForm = reactive({
  amount: 0.01,
  payType: 'ALIPAY'
})

const orderRules = {
  amount: [{ required: true, message: '请输入充值金额', trigger: 'blur' }],
  payType: [{ required: true, message: '请选择支付方式', trigger: 'change' }]
}

const formatOrderStatus = (status) => orderStatusLabels[status] || status || '-'

const canShowPayAction = (status) => payableStatuses.includes(status)

/** 停止订单状态轮询 */
const stopOrderPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  polling.value = false
}

/** 查询订单状态并处理最终态 */
const fetchOrderStatus = async () => {
  if (!paymentQr.orderNo) return

  const order = await request.get(`/v1/recharge/order/${paymentQr.orderNo}`)
  if (!polling.value) return

  lastOrder.value = order
  pollStatusText.value = `当前状态：${formatOrderStatus(order.status)}`

  if (successStatuses.includes(order.status)) {
    stopOrderPolling()
    showQrDialog.value = false
    ElMessage.success('支付成功，积分已到账')
    await router.push({
      name: 'RechargePaySuccess',
      query: { orderNo: order.orderNo }
    })
    return
  }

  if (failedStatuses.includes(order.status)) {
    stopOrderPolling()
    ElMessage.error(`支付未完成：${formatOrderStatus(order.status)}`)
  }
}

/** 开始订单状态轮询 */
const startOrderPolling = () => {
  stopOrderPolling()
  polling.value = true
  pollStatusText.value = '正在等待支付结果...'
  fetchOrderStatus().catch(() => {})
  pollTimer = window.setInterval(() => {
    fetchOrderStatus().catch(() => {})
  }, 2000)
}

/** 二维码弹窗关闭时停止轮询 */
const handleQrDialogClosed = () => {
  stopOrderPolling()
}

/** 创建临时测试订单 */
const handleCreateOrder = async () => {
  const valid = await orderFormRef.value.validate().catch(() => false)
  if (!valid) return

  stopOrderPolling()
  creating.value = true
  try {
    const res = await request.post('/v1/recharge/order', { ...orderForm })
    lastOrder.value = res
    qrImageUrl.value = ''
    paymentQr.orderNo = ''
    paymentQr.qrCode = ''
    paymentQr.expireMinutes = ''
    pollStatusText.value = '等待扫码支付'
    ElMessage.success(`测试订单创建成功：${res.orderNo}`)
  } finally {
    creating.value = false
  }
}

/** 刷新当前测试订单 */
const refreshLastOrder = async () => {
  if (!lastOrder.value?.orderNo) return
  lastOrder.value = await request.get(`/v1/recharge/order/${lastOrder.value.orderNo}`)
}

/** 生成并展示支付宝二维码 */
const handleShowQr = async () => {
  if (!lastOrder.value) return

  generatingQr.value = true
  try {
    const res = await request.post('/v1/recharge/pay/alipay/precreate', {
      orderNo: lastOrder.value.orderNo
    })
    paymentQr.orderNo = res.orderNo
    paymentQr.qrCode = res.qrCode
    paymentQr.expireMinutes = res.expireMinutes
    qrImageUrl.value = await QRCode.toDataURL(res.qrCode, {
      width: 260,
      margin: 1,
      errorCorrectionLevel: 'M'
    })
    showQrDialog.value = true
    startOrderPolling()
  } catch (error) {
    await refreshLastOrder().catch(() => {})
    if (lastOrder.value?.status) {
      pollStatusText.value = `当前状态：${formatOrderStatus(lastOrder.value.status)}`
    }
  } finally {
    generatingQr.value = false
  }
}

onBeforeUnmount(() => {
  stopOrderPolling()
})
</script>

<style scoped>
.pay-test-page {
  min-height: 100vh;
  padding: 32px;
  background: #f5f7fa;
  box-sizing: border-box;
}
.pay-test-card {
  max-width: 820px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
}
.page-alert {
  margin-bottom: 20px;
}
.order-form {
  max-width: 520px;
}
.order-result {
  margin-top: 20px;
}
.pay-actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.action-tip {
  color: #909399;
  font-size: 13px;
}
.pay-dialog {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.pay-title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}
.pay-qrcode {
  width: 260px;
  height: 260px;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}
.pay-meta {
  color: #606266;
  font-size: 13px;
}
.pay-status {
  width: 100%;
}
.pay-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  text-align: center;
  line-height: 1.6;
}
</style>