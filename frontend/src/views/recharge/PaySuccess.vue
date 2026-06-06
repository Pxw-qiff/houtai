<template>
  <div class="pay-success-page">
    <el-card class="pay-success-card">
      <el-result icon="success" title="支付成功" sub-title="订单已完成支付并入账">
        <template #extra>
          <div class="success-actions">
            <el-button type="primary" @click="handleBackToTest">继续测试支付</el-button>
            <el-button @click="handleBackToRecharge">返回充值管理</el-button>
          </div>
        </template>
      </el-result>

      <el-descriptions v-if="order" title="订单信息" :column="2" border class="order-info">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag type="success">{{ formatOrderStatus(order.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户UUID">{{ order.userUuid }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ order.payType }}</el-descriptions-item>
        <el-descriptions-item label="支付金额">{{ order.amount }} 元</el-descriptions-item>
        <el-descriptions-item label="到账积分">{{ formatPoints(order.points) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ order.payTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入账时间">{{ order.creditedTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-else-if="orderNo"
        title="正在读取订单信息..."
        type="info"
        show-icon
        :closable="false"
        class="state-alert"
      />
      <el-alert
        v-else
        title="未找到订单号，请从支付测试页重新发起支付。"
        type="warning"
        show-icon
        :closable="false"
        class="state-alert"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../utils/request.js'
import { formatPoints } from '../../utils/decimal.js'

const route = useRoute()
const router = useRouter()
const orderNo = ref(route.query.orderNo || '')
const order = ref(null)
const orderStatusLabels = {
  CREATED: '本地订单已创建',
  WAIT_PAY: '等待支付',
  PRECREATE_UNKNOWN: '预下单状态未知',
  PRECREATE_FAILED: '预下单失败',
  PAID: '已支付，待入账',
  CREDITED: '已入账',
  FAILED: '支付失败',
  TIMEOUT: '支付超时',
  CANCELLED: '已取消',
  EXCEPTION: '入账异常'
}

const formatOrderStatus = (status) => orderStatusLabels[status] || status || '-'

/** 读取支付成功后的订单信息 */
const loadOrder = async () => {
  if (!orderNo.value) return
  order.value = await request.get(`/v1/recharge/order/${orderNo.value}`)
}

/** 返回临时支付测试页 */
const handleBackToTest = () => {
  router.push({ name: 'RechargePayTest' })
}

/** 返回充值管理页 */
const handleBackToRecharge = () => {
  router.push({ name: 'RechargeManage' })
}

onMounted(() => {
  loadOrder()
})
</script>

<style scoped>
.pay-success-page {
  min-height: 100vh;
  padding: 32px;
  background: #f5f7fa;
  box-sizing: border-box;
}
.pay-success-card {
  max-width: 860px;
  margin: 0 auto;
}
.success-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
.order-info {
  margin-top: 12px;
}
.state-alert {
  margin-top: 16px;
}
</style>