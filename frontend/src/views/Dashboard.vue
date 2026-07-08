<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">数据总览</h2>
        <div class="page-subtitle">全楼宇办公桌椅与工位绑定情况统计</div>
      </div>
      <el-button type="primary" @click="handleExportAll">
        <el-icon><Download /></el-icon>
        <span>导出全部楼层明细</span>
      </el-button>
    </div>

    <el-row :gutter="16" class="mb-20">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <el-card class="card-shadow stat-card" :body-style="{ padding: '20px' }">
          <div class="flex-between">
            <div>
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-sub">{{ card.sub }}</div>
            </div>
            <div class="stat-icon" :style="{ background: card.bgColor }">
              <el-icon :size="28" :color="card.color"><component :is="card.icon" /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card class="card-shadow" :body-style="{ padding: '20px' }">
          <div class="flex-between mb-20">
            <span class="section-title">楼层绑定情况分布</span>
          </div>
          <div style="height: 360px;">
            <v-chart :option="chartOption" autoresize />
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="card-shadow" :body-style="{ padding: '20px' }">
          <div class="flex-between mb-20">
            <span class="section-title">绑定状态占比</span>
          </div>
          <div style="height: 360px;">
            <v-chart :option="pieOption" autoresize />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="card-shadow mt-20" :body-style="{ padding: '20px' }">
      <div class="flex-between mb-20">
        <span class="section-title">楼层明细速览</span>
      </div>
      <el-table :data="stats?.floorStats || []" stripe style="width: 100%">
        <el-table-column prop="floor" label="楼层" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="primary" effect="plain">{{ row.floor }}层</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="total" label="资产总数" align="center" width="120" />
        <el-table-column label="已绑定" align="center" width="150">
          <template #default="{ row }">
            <span class="text-success">{{ row.bound }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未绑定" align="center" width="150">
          <template #default="{ row }">
            <span class="text-warning">{{ row.unbound }}</span>
          </template>
        </el-table-column>
        <el-table-column label="绑定率" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="row.total ? Math.round(row.bound / row.total * 100) : 0"
              :stroke-width="16"
              :color="progressColor(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="goToFloor(row.floor)">
              查看详情
            </el-button>
            <el-button size="small" type="success" link @click="handleExportFloor(row.floor)">
              导出明细
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataBoard, User, CircleCheck, Warning, OfficeBuilding } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DatasetComponent, TransformComponent
} from 'echarts/components'
import {
  getStatistics, exportFloor, exportAll
} from '@/api'

use([
  CanvasRenderer, BarChart, PieChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DatasetComponent, TransformComponent
])

const router = useRouter()
const stats = ref(null)

const statCards = computed(() => ([
  {
    label: '资产总数',
    value: stats.value?.total ?? 0,
    sub: '套办公桌椅',
    color: '#409EFF',
    bgColor: 'rgba(64,158,255,0.1)',
    icon: DataBoard
  },
  {
    label: '已绑定工位',
    value: stats.value?.bound ?? 0,
    sub: '正在使用中',
    color: '#67C23A',
    bgColor: 'rgba(103,194,58,0.1)',
    icon: CircleCheck
  },
  {
    label: '待分配工位',
    value: stats.value?.unbound ?? 0,
    sub: '闲置可分配',
    color: '#E6A23C',
    bgColor: 'rgba(230,162,60,0.1)',
    icon: Warning
  },
  {
    label: '管理楼层',
    value: stats.value?.floorCount ?? 0,
    sub: '个楼层',
    color: '#909399',
    bgColor: 'rgba(144,147,153,0.1)',
    icon: OfficeBuilding
  }
]))

const chartOption = computed(() => {
  const floorStats = stats.value?.floorStats || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['已绑定', '未绑定'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: floorStats.map(f => f.floor + '层'),
      axisLabel: { color: '#606266' }
    },
    yAxis: { type: 'value', axisLabel: { color: '#606266' } },
    series: [
      {
        name: '已绑定',
        type: 'bar',
        stack: 'total',
        data: floorStats.map(f => f.bound),
        itemStyle: { color: '#67C23A', borderRadius: [4, 4, 0, 0] },
        barWidth: 40
      },
      {
        name: '未绑定',
        type: 'bar',
        stack: 'total',
        data: floorStats.map(f => f.unbound),
        itemStyle: { color: '#E6A23C' },
        barWidth: 40
      }
    ]
  }
})

const pieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} 套 ({d}%)' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie',
    radius: ['45%', '75%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
    label: { show: false },
    data: [
      { value: stats.value?.bound ?? 0, name: '已绑定', itemStyle: { color: '#67C23A' } },
      { value: stats.value?.unbound ?? 0, name: '未绑定', itemStyle: { color: '#E6A23C' } }
    ]
  }]
}))

function progressColor(row) {
  const rate = row.total ? row.bound / row.total : 0
  if (rate >= 0.8) return '#67C23A'
  if (rate >= 0.5) return '#409EFF'
  return '#E6A23C'
}

async function loadStats() {
  const res = await getStatistics()
  stats.value = res.data
}

function goToFloor(floor) {
  router.push({ path: '/floors', query: { floor } })
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

async function handleExportAll() {
  const res = await exportAll()
  const disposition = res.headers['content-disposition'] || ''
  const match = disposition.match(/filename\*=UTF-8''(.+)/)
  const filename = match ? decodeURIComponent(match[1]) : '全楼层_办公资产明细.xlsx'
  downloadBlob(res, filename)
  ElMessage.success('全部楼层明细导出成功')
}

onMounted(loadStats)
</script>

<style scoped lang="scss">
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.stat-card {
  border: none;
  border-radius: 12px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin: 6px 0;
}

.stat-sub {
  font-size: 12px;
  color: #c0c4cc;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
