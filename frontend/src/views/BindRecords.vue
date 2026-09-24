<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">变更台账记录</h2>
        <div class="page-subtitle">全部工位绑定、解绑、重分配及楼层搬迁批次变更历史</div>
      </div>
      <el-button @click="loadRecords">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <el-alert v-if="filters.relocationBatchNo" class="mb-20" type="success" show-icon :closable="false"
      :title="`当前仅展示搬迁批次 ${filters.relocationBatchNo} 生成的台账记录（RELOCATE 类型，可通过批次号+家具ID唯一识别）`">
      <el-button size="small" type="primary" plain @click="clearBatchFilter">查看全部台账</el-button>
    </el-alert>

    <el-card class="card-shadow mb-20" :body-style="{ padding: '16px 20px' }">
      <el-form :inline="true" :model="filters" @submit.prevent>
        <el-form-item label="家具编号">
          <el-input v-model="filters.furnitureCode" placeholder="精确查询" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filters.operateType" placeholder="全部类型" clearable style="width: 150px">
            <el-option label="初次绑定" value="BIND" />
            <el-option label="重新绑定" value="REBIND" />
            <el-option label="楼层搬迁" value="RELOCATE" />
            <el-option label="解绑" value="UNBIND" />
          </el-select>
        </el-form-item>
        <el-form-item label="搬迁批次号">
          <el-input v-model="filters.relocationBatchNo" placeholder="如 RL202609240001" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item label="使用人">
          <el-input v-model="filters.keyword" placeholder="原/新使用人模糊查" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doFilter"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetFilter"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card-shadow" :body-style="{ padding: '16px 20px' }">
      <el-table :data="records" stripe v-loading="loading" style="width: 100%">
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="recordTime" label="操作时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.recordTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="furnitureCode" label="家具编号" width="170">
          <template #default="{ row }">
            <span class="text-primary" style="font-weight:600;cursor:pointer;" @click="filterByCode(row.furnitureCode)">
              {{ row.furnitureCode }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="typeColor(row.operateType)" effect="light">
              {{ typeText(row.operateType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="搬迁批次" width="160" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.relocationBatchNo" size="small" type="warning" effect="plain"
              style="cursor:pointer" @click="filterByBatch(row.relocationBatchNo)">
              {{ row.relocationBatchNo }}
            </el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="工位变更" min-width="200">
          <template #default="{ row }">
            <template v-if="row.operateType === 'UNBIND'">
              <el-tag type="danger" size="small" effect="plain">{{ row.oldStationCode }}</el-tag>
              <span class="text-muted" style="margin:0 6px;">→</span>
              <span class="text-danger">已解绑</span>
            </template>
            <template v-else>
              <span v-if="row.oldStationCode">
                <el-tag type="info" size="small" effect="plain">{{ row.oldStationCode }}</el-tag>
                <span class="text-muted" style="margin:0 6px;">→</span>
              </span>
              <el-tag type="success" size="small" effect="dark">{{ row.newStationCode }}</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="人员变更" min-width="240">
          <template #default="{ row }">
            <template v-if="row.operateType === 'UNBIND'">
              <div class="text-danger">{{ row.oldEmployeeName || '—' }}
                <small v-if="row.oldDepartment">({{ row.oldDepartment }})</small>
              </div>
            </template>
            <template v-else>
              <template v-if="row.oldEmployeeName">
                <span class="text-muted">{{ row.oldEmployeeName }}
                  <small v-if="row.oldDepartment">({{ row.oldDepartment }})</small>
                </span>
                <span style="margin:0 8px;color:#c0c4cc;">→</span>
              </template>
              <span style="font-weight:600;">{{ row.newEmployeeName || '未分配' }}
                <small v-if="row.newDepartment" class="text-muted">({{ row.newDepartment }})</small>
              </span>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="130" align="center" />
        <el-table-column prop="operateReason" label="操作原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.operateReason || '—' }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap mt-20">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          small
          @size-change="loadRecords"
          @current-change="loadRecords"
        />
      </div>
    </el-card>

    <el-dialog v-model="timelineVisible" title="家具完整变更历史" width="600px" destroy-on-close>
      <el-timeline v-if="codeRecords.length > 0">
        <el-timeline-item
          v-for="(item, idx) in codeRecords"
          :key="item.id"
          :timestamp="formatTime(item.recordTime)"
          :type="typeColor(item.operateType)"
          placement="top"
          :hollow="idx === 0"
        >
          <el-card shadow="never" :body-style="{ padding: '12px 16px' }">
            <div class="flex-between mb-6">
              <el-tag size="small" :type="typeColor(item.operateType)" effect="light">
                {{ typeText(item.operateType) }}
              </el-tag>
              <el-tag v-if="item.relocationBatchNo" size="small" type="warning" effect="plain" class="ml-6">
                {{ item.relocationBatchNo }}
              </el-tag>
              <span class="text-muted" style="font-size:12px;margin-left:auto;">操作人：{{ item.operatorName }}</span>
            </div>
            <div class="tl-line">
              <span>工位：</span>
              <template v-if="item.operateType === 'UNBIND'">
                <s>{{ item.oldStationCode }}</s>
                <span class="text-danger ml-6">(已解绑)</span>
              </template>
              <template v-else>
                <template v-if="item.oldStationCode">
                  <s class="text-muted">{{ item.oldStationCode }}</s>
                  <el-icon class="ml-6 mr-6"><ArrowRight /></el-icon>
                </template>
                <span style="color:#67C23A;font-weight:600;">{{ item.newStationCode }}</span>
              </template>
            </div>
            <div class="tl-line">
              <span>人员：</span>
              <template v-if="item.operateType === 'UNBIND'">
                <s>{{ item.oldEmployeeName || '—' }}</s>
              </template>
              <template v-else>
                <template v-if="item.oldEmployeeName">
                  <s class="text-muted">{{ item.oldEmployeeName }}</s>
                  <el-icon class="ml-6 mr-6"><ArrowRight /></el-icon>
                </template>
                <span style="color:#409EFF;font-weight:600;">{{ item.newEmployeeName || '未分配' }}</span>
              </template>
            </div>
            <div class="tl-line text-muted" v-if="item.operateReason">
              <el-icon><ChatDotRound /></el-icon>
              <span class="ml-4">{{ item.operateReason }}</span>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无变更历史" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  Refresh, Search, RefreshLeft, ArrowRight, ChatDotRound
} from '@element-plus/icons-vue'
import { pageRecords, getRecordsByFurnitureCode } from '@/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const codeRecords = ref([])
const timelineVisible = ref(false)

const filters = reactive({
  furnitureCode: '',
  operateType: '',
  keyword: '',
  relocationBatchNo: ''
})

const page = ref(1)
const size = ref(20)

function formatTime(t) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '—'
}

function typeText(type) {
  return { BIND: '初次绑定', REBIND: '重新绑定', RELOCATE: '楼层搬迁', UNBIND: '解绑' }[type] || type
}

function typeColor(type) {
  return { BIND: 'success', REBIND: 'warning', RELOCATE: 'warning', UNBIND: 'danger' }[type] || 'info'
}

async function loadRecords() {
  loading.value = true
  try {
    const res = await pageRecords({
      furnitureCode: filters.furnitureCode || undefined,
      operateType: filters.operateType || undefined,
      keyword: filters.keyword || undefined,
      relocationBatchNo: filters.relocationBatchNo || undefined,
      page: page.value - 1,
      size: size.value
    })
    records.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } finally {
    loading.value = false
  }
}

function doFilter() {
  page.value = 1
  loadRecords()
}

function resetFilter() {
  filters.furnitureCode = ''
  filters.operateType = ''
  filters.keyword = ''
  filters.relocationBatchNo = ''
  page.value = 1
  loadRecords()
}

function clearBatchFilter() {
  filters.relocationBatchNo = ''
  router.replace({ query: {} })
  doFilter()
}

function filterByBatch(batchNo) {
  filters.relocationBatchNo = batchNo
  doFilter()
}

async function filterByCode(code) {
  filters.furnitureCode = code
  doFilter()
  try {
    const res = await getRecordsByFurnitureCode(code)
    codeRecords.value = res.data || []
    timelineVisible.value = true
  } catch (e) {}
}

onMounted(() => {
  if (route.query.batchNo) {
    filters.relocationBatchNo = String(route.query.batchNo)
  }
  if (route.query.code) {
    filters.furnitureCode = String(route.query.code)
  }
  loadRecords()
})
</script>

<style scoped lang="scss">
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.mb-6 {
  margin-bottom: 6px;
}

.ml-4 {
  margin-left: 4px;
}

.ml-6 {
  margin-left: 6px;
}

.mr-6 {
  margin-right: 6px;
}

.text-danger {
  color: #F56C6C;
}

.tl-line {
  font-size: 13px;
  margin: 4px 0;
  display: flex;
  align-items: center;
}

s {
  text-decoration: line-through;
}
</style>
