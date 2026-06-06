<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="form">
        <el-form-item label="用户UUID">
          <el-input v-model="form.userUuid" placeholder="输入用户UUID" clearable style="width: 260px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="form.bizType" placeholder="全部" clearable style="width: 140px">
            <el-option label="充值" value="RECHARGE" />
            <el-option label="消费" value="CONSUME" />
            <el-option label="退款" value="REFUND" />
            <el-option label="管理员加" value="ADMIN_ADD" />
            <el-option label="管理员扣" value="ADMIN_DEDUCT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :loading="loading" :icon="Search">查询</el-button>
          <el-button @click="handleReset" :icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="page-card">
      <el-table :data="tableData" border stripe v-loading="loading" empty-text="暂无流水记录">
        <el-table-column prop="flowNo" label="流水号" width="200" />
        <el-table-column prop="bizType" label="业务类型" width="120">
          <template #default="{ row }">
            <el-tag :type="bizTypeTag(row.bizType)" size="small" effect="dark">{{ row.bizType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="beforeAvailablePoints" label="变动前可用" width="110" align="right">
          <template #default="{ row }">{{ formatPoints(row.beforeAvailablePoints) }}</template>
        </el-table-column>
        <el-table-column prop="changeAvailablePoints" label="变动量" width="110" align="right">
          <template #default="{ row }">
            <span :class="isPositiveDecimal(row.changeAvailablePoints) ? 'text-up' : 'text-down'">
              {{ isPositiveDecimal(row.changeAvailablePoints) ? '+' : '' }}{{ formatPoints(row.changeAvailablePoints) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="afterAvailablePoints" label="变动后可用" width="110" align="right">
          <template #default="{ row }">{{ formatPoints(row.afterAvailablePoints) }}</template>
        </el-table-column>
        <el-table-column prop="bizOrderNo" label="关联单号" width="200" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleQuery"
          @current-change="handleQuery"
          background
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import request from '../../utils/request.js'
import { formatPoints, isPositiveDecimal } from '../../utils/decimal.js'

const form = ref({ userUuid: '', bizType: '' })
const tableData = ref([])
const loading = ref(false)
const pagination = reactive({ current: 1, size: 10, total: 0 })

const bizTypeTag = (type) => {
  const map = { RECHARGE: 'success', CONSUME: 'warning', REFUND: 'info', ADMIN_ADD: '', ADMIN_DEDUCT: 'danger',
    TASK_FREEZE: 'warning', TASK_RETURN: 'info', TASK_UNFREEZE: '' }
  return map[type] || ''
}

const handleQuery = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
    }
    if (form.value.userUuid) params.userUuid = form.value.userUuid
    if (form.value.bizType) params.bizType = form.value.bizType

    const res = await request.get('/v1/admin/credit/flows', { params })
    pagination.total = res.total || 0
    tableData.value = res.records || []
  } catch (e) {
    console.error('查询流水失败', e)
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  form.value = { userUuid: '', bizType: '' }
  pagination.current = 1
  handleQuery()
}
</script>

<style scoped>
.text-up {
  color: #67c23a;
  font-weight: 600;
}
.text-down {
  color: #f56c6c;
  font-weight: 600;
}
</style>