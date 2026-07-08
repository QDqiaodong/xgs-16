<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">楼层分组视图</h2>
        <div class="page-subtitle">按楼层分组展示全部办公桌椅绑定情况</div>
      </div>
      <div class="header-actions">
        <el-input
          v-model="keyword"
          placeholder="搜索编号/款式/工位/使用人"
          style="width: 260px"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="filterFloor"
          placeholder="按楼层筛选"
          style="width: 140px"
          clearable
          @change="handleSearch"
        >
          <el-option v-for="f in allFloors" :key="f" :label="f + '层'" :value="f" />
        </el-select>
        <el-select
          v-model="filterBind"
          placeholder="绑定状态"
          style="width: 120px"
          clearable
          @change="handleSearch"
        >
          <el-option label="已绑定" :value="1" />
          <el-option label="未绑定" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>筛选
        </el-button>
      </div>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col
        v-for="floor in floorKeys"
        :key="floor"
        :xs="24" :sm="24" :md="12" :lg="8" :xl="6"
        class="mb-20"
      >
        <el-card class="floor-card card-shadow" :body-style="{ padding: 0 }">
          <div class="floor-header" :style="{ background: floorColor(floor) }">
            <div>
              <div class="floor-num">{{ floor }}层</div>
              <div class="floor-sub">
                共 {{ groupData[floor].length }} 套
                · 已绑定 {{ groupData[floor].filter(f=>f.bindStatus===1).length }}
              </div>
            </div>
            <div class="floor-actions">
              <el-tooltip content="查看全部桌椅">
                <el-button circle size="small" type="primary" plain @click="viewFloorFurniture(floor)">
                  <el-icon><Files /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="导出楼层明细">
                <el-button circle size="small" type="success" plain @click="handleExportFloor(floor)">
                  <el-icon><Download /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>

          <div class="floor-items">
            <el-empty
              v-if="groupData[floor].length === 0"
              description="暂无桌椅"
              :image-size="80"
            />
            <div v-else class="item-scroll">
              <div
                v-for="item in groupData[floor]"
                :key="item.id"
                class="furniture-item"
                :class="{ bound: item.bindStatus === 1 }"
                @click="viewDetail(item)"
              >
                <div class="item-left">
                  <div class="item-type" :class="getTypeClass(item.furnitureType)">
                    {{ getTypeIcon(item.furnitureType) }}
                  </div>
                  <div class="item-info">
                    <div class="item-code">{{ item.furnitureCode }}</div>
                    <div class="item-name">{{ item.styleName || item.brand || '未填写' }}</div>
                  </div>
                </div>
                <div class="item-right">
                  <el-tag
                    v-if="item.bindStatus === 1"
                    type="success"
                    size="small"
                    effect="light"
                    class="mb-4"
                  >
                    {{ item.stationCode }}
                  </el-tag>
                  <el-tag v-else type="info" size="small" effect="light" class="mb-4">未绑定</el-tag>
                  <div class="item-user" v-if="item.bindStatus === 1">
                    {{ item.employeeName || '—' }}
                    <span class="text-muted" v-if="item.department"> / {{ item.department }}</span>
                  </div>
                </div>
              </div>
              <div v-if="groupData[floor].length > 8" class="more-hint" @click="viewFloorFurniture(floor)">
                还有 {{ groupData[floor].length - 8 }} 项，点击查看全部
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="detailVisible"
      title="家具详情"
      width="680px"
      destroy-on-close
    >
      <el-descriptions v-if="currentItem" :column="2" border size="default">
        <el-descriptions-item label="桌椅编号">{{ currentItem.furnitureCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentItem.furnitureType }}</el-descriptions-item>
        <el-descriptions-item label="款式">{{ currentItem.styleName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ currentItem.brand || '—' }}</el-descriptions-item>
        <el-descriptions-item label="尺寸">{{ currentItem.sizeSpec || '—' }}</el-descriptions-item>
        <el-descriptions-item label="价格">
          {{ currentItem.price ? '¥' + currentItem.price : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="楼层区域">{{ currentItem.floorNum }}层 {{ currentItem.areaName || '' }}</el-descriptions-item>
        <el-descriptions-item label="绑定状态">
          <el-tag :type="currentItem.bindStatus === 1 ? 'success' : 'info'" size="small">
            {{ currentItem.bindStatus === 1 ? '已绑定' : '未绑定' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="工位编号" v-if="currentItem.bindStatus === 1">
          {{ currentItem.stationCode }}
        </el-descriptions-item>
        <el-descriptions-item label="使用人/部门" v-if="currentItem.bindStatus === 1">
          {{ currentItem.employeeName || '—' }} / {{ currentItem.department || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentItem.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="viewRecords(currentItem)">查看变更台账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Files, Download } from '@element-plus/icons-vue'
import {
  getGroupByFloor, listFurniture, getFurnitureByFloor, exportFloor
} from '@/api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const groupData = ref({})
const allFloors = ref([])
const keyword = ref('')
const filterFloor = ref(null)
const filterBind = ref(null)
const detailVisible = ref(false)
const currentItem = ref(null)

const floorKeys = computed(() => {
  const keys = Object.keys(groupData.value).map(Number).sort((a, b) => a - b)
  return keys
})

function floorColor(floor) {
  const colors = [
    'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    'linear-gradient(135deg, #30cfd0 0%, #330867 100%)',
    'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
    'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)'
  ]
  return colors[floor % colors.length]
}

function getTypeClass(type) {
  const map = { '办公桌': 'desk', '办公椅': 'chair', '会议桌': 'mt', '培训桌': 'tr', '老板桌': 'ex' }
  return map[type] || 'def'
}

function getTypeIcon(type) {
  const map = { '办公桌': '桌', '办公椅': '椅', '会议桌': '会', '培训桌': '培', '老板桌': '总' }
  return map[type] || '家'
}

async function loadData() {
  loading.value = true
  try {
    let dataMap = {}
    if (keyword.value || filterFloor != null || filterBind != null) {
      const res = await listFurniture({
        keyword: keyword.value || undefined,
        floorNum: filterFloor.value,
        bindStatus: filterBind.value
      })
      const list = res.data || []
      list.forEach(item => {
        if (!dataMap[item.floorNum]) dataMap[item.floorNum] = []
        dataMap[item.floorNum].push(item)
      })
    } else {
      const res = await getGroupByFloor()
      dataMap = res.data || {}
    }
    groupData.value = dataMap
    if (allFloors.value.length === 0) {
      allFloors.value = Object.keys(dataMap).map(Number).sort((a, b) => a - b)
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function viewFloorFurniture(floor) {
  router.push({ path: '/furniture', query: { floor } })
}

function viewDetail(item) {
  currentItem.value = item
  detailVisible.value = true
}

function viewRecords(item) {
  router.push({ path: '/records', query: { code: item.furnitureCode } })
}

function downloadBlob(response, filename) {
  const url = window.URL.createObjectURL(new Blob([response.data]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

async function handleExportFloor(floor) {
  const res = await exportFloor(floor)
  const disposition = res.headers['content-disposition'] || ''
  const match = disposition.match(/filename\*=UTF-8''(.+)/)
  const filename = match ? decodeURIComponent(match[1]) : `${floor}层_办公资产明细.xlsx`
  downloadBlob(res, filename)
  ElMessage.success(`${floor}层明细导出成功`)
}

onMounted(async () => {
  await loadData()
  const f = route.query.floor
  if (f) {
    filterFloor.value = Number(f)
    handleSearch()
  }
})
</script>

<style scoped lang="scss">
.header-actions {
  display: flex;
  gap: 10px;
}

.floor-card {
  border: none;
  border-radius: 14px;
  overflow: hidden;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }
}

.floor-header {
  padding: 18px 20px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .floor-num {
    font-size: 22px;
    font-weight: 700;
  }

  .floor-sub {
    font-size: 13px;
    opacity: 0.9;
    margin-top: 4px;
  }

  .floor-actions {
    display: flex;
    gap: 8px;
  }
}

.floor-items {
  padding: 12px;
  max-height: 420px;
}

.item-scroll {
  max-height: 396px;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 2px;
  }
}

.furniture-item {
  padding: 10px 12px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  margin-bottom: 6px;
  border: 1px solid transparent;
  transition: all 0.2s;

  &:hover {
    background: #f5f7fa;
    border-color: #ebeef5;
  }

  &.bound {
    .item-right .el-tag {
      margin-bottom: 2px;
    }
  }
}

.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.item-type {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;

  &.desk { background: #409EFF; }
  &.chair { background: #67C23A; }
  &.mt { background: #E6A23C; }
  &.tr { background: #909399; }
  &.ex { background: #F56C6C; }
  &.def { background: #909399; }
}

.item-info {
  min-width: 0;
}

.item-code {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-name {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}

.item-right {
  text-align: right;
  flex-shrink: 0;
}

.item-user {
  font-size: 12px;
  color: #606266;
}

.mb-4 {
  margin-bottom: 4px;
}

.more-hint {
  text-align: center;
  padding: 10px;
  color: #409EFF;
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  background: #ecf5ff;

  &:hover {
    background: #d9ecff;
  }
}
</style>
