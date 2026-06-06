<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="form">
        <el-form-item label="搜索">
          <el-input
            v-model="form.keyword"
            placeholder="用户名 / 邮箱"
            clearable
            style="width: 260px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :icon="Search">查询</el-button>
          <el-button @click="handleReset" :icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="page-card">
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        empty-text="暂无积分账户数据"
      >
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="availablePoints" label="可用积分" width="120" align="right">
          <template #default="{ row }">
            <span class="value-highlight">{{ formatPoints(row.availablePoints) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="frozenPoints" label="冻结积分" width="110" align="right">
          <template #default="{ row }">{{ formatPoints(row.frozenPoints) }}</template>
        </el-table-column>
        <el-table-column prop="totalPoints" label="总积分" width="110" align="right">
          <template #default="{ row }">{{ formatPoints(row.totalPoints) }}</template>
        </el-table-column>
        <el-table-column prop="totalRechargePoints" label="累计充值" width="110" align="right">
          <template #default="{ row }">{{ formatPoints(row.totalRechargePoints) }}</template>
        </el-table-column>
        <el-table-column prop="totalConsumePoints" label="累计消费" width="110" align="right">
          <template #default="{ row }">{{ formatPoints(row.totalConsumePoints) }}</template>
        </el-table-column>
        <el-table-column prop="totalRefundPoints" label="累计退款" width="110" align="right">
          <template #default="{ row }">{{ formatPoints(row.totalRefundPoints) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.status === 1 ? '正常' : '冻结' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="dialogVisible" title="积分账户详情" width="600px" destroy-on-close>
      <el-descriptions v-if="detail" :column="3" border>
        <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户UUID" :span="3">{{ detail.userUuid }}</el-descriptions-item>
        <el-descriptions-item label="可用积分">
          <el-tag type="success" effect="dark" size="large">{{ formatPoints(detail.availablePoints) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="冻结积分">{{ formatPoints(detail.frozenPoints) }}</el-descriptions-item>
        <el-descriptions-item label="总积分">{{ formatPoints(detail.totalPoints) }}</el-descriptions-item>
        <el-descriptions-item label="累计充值">{{ formatPoints(detail.totalRechargePoints) }}</el-descriptions-item>
        <el-descriptions-item label="累计消费">{{ formatPoints(detail.totalConsumePoints) }}</el-descriptions-item>
        <el-descriptions-item label="累计退款">{{ formatPoints(detail.totalRefundPoints) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 1 ? 'success' : 'danger'" effect="dark">
            {{ detail.status === 1 ? '正常' : '冻结' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import request from '../../utils/request.js'
import { formatPoints } from '../../utils/decimal.js'

const form = ref({ keyword: '' })
const tableData = ref([])
const loading = ref(false)
const pagination = reactive({ current: 1, size: 10, total: 0 })

const dialogVisible = ref(false)
const detail = ref(null)

const handleQuery = () => {
  loading.value = true
  request.get('/v1/admin/credit/accounts', {
    params: {
      current: pagination.current,
      size: pagination.size,
      keyword: form.value.keyword || undefined
    }
  }).then(res => {
    tableData.value = res.records || []
    pagination.total = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

const handleReset = () => {
  form.value.keyword = ''
  pagination.current = 1
  handleQuery()
}

const showDetail = (row) => {
  detail.value = row
  dialogVisible.value = true
}

onMounted(() => {
  handleQuery()
})
</script>

<style scoped>
.value-highlight {
  font-weight: 600;
  color: #303133;
}
</style>