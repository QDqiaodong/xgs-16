<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">楼层搬迁交接台</h2>
        <div class="page-subtitle">把部门搬迁整理成独立批次：逐项校验、原子执行、失败可重试，台账自动留痕</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建搬迁批次</el-button>
    </div>

    <!-- 统计条 -->
    <el-row :gutter="16" class="mb-20">
      <el-col :span="4" v-for="card in statCards" :key="card.key">
        <el-card class="card-shadow stat-card" :body-style="{ padding: '16px 20px' }">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card class="card-shadow mb-20" :body-style="{ padding: '16px 20px' }">
      <el-form :inline="true" :model="filters" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="批次号/名称/部门" clearable style="width: 200px" @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="迁出楼层">
          <el-select v-model="filters.sourceFloorNum" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="f in floors" :key="'s' + f" :label="f + '层'" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="迁入楼层">
          <el-select v-model="filters.targetFloorNum" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="f in floors" :key="'t' + f" :label="f + '层'" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 批次列表 -->
    <el-card class="card-shadow" :body-style="{ padding: '12px 16px' }">
      <el-table :data="list" stripe v-loading="loading" style="width: 100%">
        <el-table-column label="批次号" width="160">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.batchNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="batchName" label="批次名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="搬迁路线" width="150" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ row.sourceFloorNum }}F</el-tag>
            <el-icon class="flow-arrow"><Right /></el-icon>
            <el-tag size="small" type="success" effect="dark">{{ row.targetFloorNum }}F</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="110" align="center">
          <template #default="{ row }">{{ row.department || '—' }}</template>
        </el-table-column>
        <el-table-column label="进度" width="170" align="center">
          <template #default="{ row }">
            <el-progress :percentage="progressOf(row)" :status="progressStatus(row)" :stroke-width="14"
              :format="() => `${row.successCount || 0}/${row.totalCount || 0}`" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" effect="light">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" align="center" />
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PARTIAL_FAILED'" size="small" link type="warning"
              @click="openDetail(row)">查看失败项</el-button>
            <el-button v-if="canEdit(row.status)" size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap mt-20">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          small
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- ================= 新建/编辑批次抽屉 ================= -->
    <el-drawer v-model="formVisible" :title="editingId ? '编辑搬迁批次' : '新建搬迁批次'" size="82%"
      :close-on-click-modal="false" destroy-on-close>
      <div class="drawer-body">
        <el-form :model="form" :rules="formRules" ref="formRef" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="批次名称" prop="batchName">
                <el-input v-model="form.batchName" placeholder="如：技术部 2F→4F 整体搬迁" maxlength="200" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="迁出楼层" prop="sourceFloorNum">
                <el-select v-model="form.sourceFloorNum" style="width: 100%">
                  <el-option v-for="f in floors" :key="'fs' + f" :label="f + '层'" :value="f" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="迁入楼层" prop="targetFloorNum">
                <el-select v-model="form.targetFloorNum" style="width: 100%">
                  <el-option v-for="f in floors" :key="'ft' + f" :label="f + '层'" :value="f" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="部门">
                <el-input v-model="form.department" placeholder="搬迁部门" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="操作人">
                <el-input v-model="form.operatorName" maxlength="100" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>

        <div class="pick-toolbar">
          <el-button type="primary" plain :icon="Plus" @click="openFurniturePicker">添加家具</el-button>
          <el-button type="danger" plain :icon="Delete" :disabled="form.items.length === 0"
            @click="form.items = []">清空</el-button>
          <span class="pick-tip">已选 {{ form.items.length }} 件；同一件家具不能重复加入，目标工位在批次内不可重复</span>
        </div>

        <el-table :data="form.items" border size="small" style="width: 100%" empty-text="请从迁出楼层选择家具">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="furnitureCode" label="家具编号" width="190" />
          <el-table-column label="原绑定（创建快照）" min-width="240">
            <template #default="{ row }">
              <div class="snap-cell">
                <el-tag size="small" type="info" effect="plain">{{ row._floor }}F {{ row.snapshotStationCode || '未绑定' }}</el-tag>
                <span>{{ row.snapshotEmployeeName || '无人' }}<small class="text-muted"> / {{ row.snapshotDepartment || '无部门' }}</small></span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="目标工位" width="180">
            <template #default="{ row }">
              <el-input v-model="row.targetStationCode" size="small"
                :class="{ 'station-dup': isDupStation(row.targetStationCode) }"
                @blur="onStationInput(row)" placeholder="如 G4-A001" />
            </template>
          </el-table-column>
          <el-table-column label="目标使用人" width="140">
            <template #default="{ row }">
              <el-input v-model="row.targetEmployeeName" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="目标部门" width="140">
            <template #default="{ row }">
              <el-input v-model="row.targetDepartment" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="校验" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="stationFloorWarn(row)" type="danger" size="small">工位楼层不符</el-tag>
              <el-tag v-else-if="isDupStation(row.targetStationCode)" type="danger" size="small">工位重复</el-tag>
              <el-tag v-else type="success" size="small" effect="plain">OK</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button size="small" link type="danger" @click="form.items.splice($index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="drawer-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveBatch(false)">保存为待确认</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- ================= 家具选择对话框 ================= -->
    <el-dialog v-model="pickerVisible" title="选择要搬迁的家具" width="900px" append-to-body destroy-on-close>
      <div class="picker-filter">
        <el-input v-model="pickerKeyword" placeholder="编号/工位/使用人" clearable style="width: 220px" @keyup.enter="loadPicker" />
        <el-select v-model="pickerFloor" placeholder="楼层" style="width: 110px" @change="loadPicker">
          <el-option v-for="f in floors" :key="'pf' + f" :label="f + '层'" :value="f" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadPicker">查询</el-button>
        <span class="pick-tip">仅展示可加入的家具；已处于其他未结束批次中的家具会被标记</span>
      </div>
      <el-table :data="pickerList" border size="small" height="420"
        @selection-change="onPickerSelection" ref="pickerTableRef"
        :row-key="(r) => r.id" :row-class-name="pickerRowClass" stripe>
        <el-table-column type="selection" width="48" :selectable="(r) => !r._locked" reserve-selection />
        <el-table-column prop="furnitureCode" label="家具编号" width="180" />
        <el-table-column prop="furnitureType" label="类型" width="90" />
        <el-table-column prop="floorNum" label="楼层" width="70" align="center">
          <template #default="{ row }">{{ row.floorNum }}F</template>
        </el-table-column>
        <el-table-column prop="stationCode" label="当前工位" width="130">
          <template #default="{ row }">{{ row.stationCode || '未绑定' }}</template>
        </el-table-column>
        <el-table-column prop="employeeName" label="使用人" width="100">
          <template #default="{ row }">{{ row.employeeName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="100" />
        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row._locked" type="danger" size="small">在其他搬迁批次中</el-tag>
            <el-tag v-else-if="isInForm(row.id)" type="warning" size="small">已在本批次</el-tag>
            <el-tag v-else type="success" size="small" effect="plain">可选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPicker">加入所选（{{ pickerSelection.length }}）</el-button>
      </template>
    </el-dialog>

    <!-- ================= 批次详情抽屉 ================= -->
    <el-drawer v-model="detailVisible" :title="`批次详情 ${detail.batchNo || ''}`" size="86%" destroy-on-close>
      <div v-if="detail.id" class="drawer-body" v-loading="detailLoading">
        <!-- 概要 -->
        <el-descriptions :column="4" border size="small" class="mb-20">
          <el-descriptions-item label="批次名称">{{ detail.batchName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta(detail.status).type">{{ statusMeta(detail.status).label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="搬迁路线">{{ detail.sourceFloorNum }}层 → {{ detail.targetFloorNum }}层</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detail.department || '—' }}</el-descriptions-item>
          <el-descriptions-item label="成功/失败/总数">
            <span style="color:#67C23A;font-weight:600">{{ detail.successCount || 0 }}</span> /
            <span :style="{color: (detail.failCount||0)>0 ? '#F56C6C':'#909399',fontWeight:600}">{{ detail.failCount || 0 }}</span> /
            {{ detail.totalCount || 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operatorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmt(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="确认/执行/完成">
            {{ fmt(detail.confirmedAt) }} / {{ fmt(detail.executedAt) }} / {{ fmt(detail.completedAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.revokedAt" label="撤销时间" :span="1">
            <span style="color:#909399">{{ fmt(detail.revokedAt) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="4">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 操作区 -->
        <div class="action-bar mb-20">
          <template v-if="detail.status === 'PENDING'">
            <el-button type="primary" :loading="validating" @click="runValidate(false)">逐项校验</el-button>
            <el-button type="success" :loading="confirming" @click="runValidate(true)">校验并确认</el-button>
            <el-button type="info" plain @click="doCancel">撤销批次</el-button>
            <el-button type="warning" plain @click="openEdit(detail)">编辑内容</el-button>
          </template>
          <template v-else-if="detail.status === 'READY'">
            <el-button type="success" :loading="executing" @click="doExecute">开始执行搬迁</el-button>
            <el-button type="warning" plain :loading="withdrawing" @click="doWithdraw">撤回确认</el-button>
            <el-button type="info" plain @click="doCancel">撤销批次</el-button>
          </template>
          <template v-else-if="detail.status === 'RUNNING'">
            <el-button :loading="true">执行中…</el-button>
            <el-button @click="loadDetail">手动刷新</el-button>
          </template>
          <template v-else-if="detail.status === 'PARTIAL_FAILED'">
            <el-button type="warning" :loading="executing" @click="doExecute">重试失败项（{{ detail.failCount }}）</el-button>
            <el-button @click="loadDetail">刷新结果</el-button>
          </template>
          <template v-else-if="detail.status === 'COMPLETED'">
            <el-tag type="success">全部条目搬迁完成，台账已生成</el-tag>
          </template>
          <template v-else-if="detail.status === 'CANCELLED'">
            <el-tag type="info">批次已撤销，原绑定快照仍可在下方追溯</el-tag>
          </template>
        </div>

        <!-- 校验结果横幅 -->
        <el-alert v-if="validation" class="mb-20"
          :title="validation.passable ? `逐项校验通过（${validation.totalCount} 项），可以确认执行` : `校验发现 ${validation.problemCount} 项冲突，存在冲突不得进入可执行状态`"
          :type="validation.passable ? 'success' : 'error'" :closable="false" show-icon />

        <!-- 逐项结果 -->
        <el-tabs v-model="detailTab">
          <el-tab-pane label="逐项结果" name="items">
            <div class="table-toolbar">
              <el-radio-group v-model="itemFilter" size="small">
                <el-radio-button label="ALL">全部({{ detail.items?.length || 0 }})</el-radio-button>
                <el-radio-button label="SUCCESS">成功({{ detail.successCount || 0 }})</el-radio-button>
                <el-radio-button label="FAILED">失败({{ detail.failCount || 0 }})</el-radio-button>
                <el-radio-button label="PENDING">待执行</el-radio-button>
              </el-radio-group>
            </div>
            <el-table :data="filteredItems" border size="small" style="width:100%" max-height="520">
              <el-table-column type="index" label="#" width="50" align="center" />
              <el-table-column prop="furnitureCode" label="家具编号" width="180" />
              <el-table-column label="原绑定快照" min-width="230">
                <template #default="{ row }">
                  <div class="snap-cell">
                    <el-tag size="small" type="info" effect="plain">{{ row.snapshotFloorNum }}F {{ row.snapshotStationCode || '未绑定' }}</el-tag>
                    <span>{{ row.snapshotEmployeeName || '无人' }}<small class="text-muted"> / {{ row.snapshotDepartment || '无部门' }}</small></span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="目标" min-width="220">
                <template #default="{ row }">
                  <div class="snap-cell">
                    <el-tag size="small" type="success" effect="dark">{{ row.targetStationCode }}</el-tag>
                    <span>{{ row.targetEmployeeName }}<small class="text-muted"> / {{ row.targetDepartment }}</small></span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="执行结果" width="230">
                <template #default="{ row }">
                  <el-tag v-if="row.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
                  <el-tag v-else-if="row.status === 'FAILED'" type="danger" size="small">失败</el-tag>
                  <el-tag v-else type="info" size="small">待执行</el-tag>
                  <el-tooltip v-if="row.status === 'FAILED'" :content="row.failReason" placement="top">
                    <span class="fail-reason">{{ failCodeText(row.failCode) }}</span>
                  </el-tooltip>
                  <div v-if="row.status === 'SUCCESS' && row.resultNote" class="result-note">{{ row.resultNote }}</div>
                  <div v-if="row.status === 'FAILED' && row.failReason" class="result-note text-danger">{{ row.failReason }}</div>
                </template>
              </el-table-column>
              <el-table-column label="台账" width="100" align="center">
                <template #default="{ row }">
                  <el-link v-if="row.recordId" type="primary" @click="goRecords(row)">#{{ row.recordId }}</el-link>
                  <span v-else class="text-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="执行时间" width="160" align="center">
                <template #default="{ row }">{{ fmt(row.executedAt) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="校验明细" name="validate">
            <el-table :data="validation?.items || []" border size="small" style="width:100%" max-height="520">
              <el-table-column type="index" label="#" width="50" align="center" />
              <el-table-column prop="furnitureCode" label="家具编号" width="180" />
              <el-table-column prop="targetStationCode" label="目标工位" width="140" />
              <el-table-column prop="targetEmployeeName" label="目标使用人" width="120" />
              <el-table-column prop="targetDepartment" label="目标部门" width="120" />
              <el-table-column label="结果" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                    {{ row.passed ? '通过' : failCodeText(row.failCode) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="300" show-overflow-tooltip />
            </el-table>
            <el-empty v-if="!validation" description="点击上方“逐项校验”查看最新校验结果" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  Plus, Delete, Search, Refresh, RefreshLeft, Right
} from '@element-plus/icons-vue'
import {
  listFurniture, getAllFloors,
  pageRelocationBatches, createRelocationBatch, updateRelocationBatch,
  getRelocationBatchDetail, validateRelocationBatch, confirmRelocationBatch,
  withdrawRelocationBatch, cancelRelocationBatch, executeRelocationBatch
} from '@/api'

const router = useRouter()

const statusOptions = [
  { value: 'PENDING', label: '待确认' },
  { value: 'READY', label: '可执行' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'PARTIAL_FAILED', label: '部分失败' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已撤销' }
]

function statusMeta(s) {
  return ({
    PENDING: { label: '待确认', type: 'info' },
    READY: { label: '可执行', type: 'primary' },
    RUNNING: { label: '执行中', type: 'warning' },
    PARTIAL_FAILED: { label: '部分失败', type: 'danger' },
    COMPLETED: { label: '已完成', type: 'success' },
    CANCELLED: { label: '已撤销', type: 'info' }
  })[s] || { label: s, type: 'info' }
}

function failCodeText(code) {
  return ({
    FURNITURE_DELETED: '家具已删除',
    SNAPSHOT_CHANGED: '原绑定已变化',
    STATION_OCCUPIED: '工位被占用',
    FLOOR_MISMATCH: '楼层不匹配',
    SOURCE_FLOOR_MISMATCH: '迁出楼层不符',
    FURNITURE_LOCKED: '在其他批次中',
    DUPLICATE_TARGET_STATION: '工位重复',
    EXEC_ERROR: '执行异常'
  })[code] || code || '—'
}

const fmt = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '—')

// ============ 列表 ============
const floors = ref([])
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const filters = reactive({ keyword: '', status: '', sourceFloorNum: null, targetFloorNum: null })

const statCards = computed(() => [
  { key: 'ALL', label: '当前筛选批次', value: total.value, color: '#409EFF' },
  { key: 'RUNNING', label: '执行中', value: list.value.filter((x) => x.status === 'RUNNING').length, color: '#E6A23C' },
  { key: 'PARTIAL_FAILED', label: '部分失败', value: list.value.filter((x) => x.status === 'PARTIAL_FAILED').length, color: '#F56C6C' },
  { key: 'COMPLETED', label: '已完成', value: list.value.filter((x) => x.status === 'COMPLETED').length, color: '#67C23A' }
])

async function loadList() {
  loading.value = true
  try {
    const res = await pageRelocationBatches({
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      sourceFloorNum: filters.sourceFloorNum ?? undefined,
      targetFloorNum: filters.targetFloorNum ?? undefined,
      page: page.value - 1,
      size: size.value
    })
    list.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.sourceFloorNum = null
  filters.targetFloorNum = null
  page.value = 1
  loadList()
}

function progressOf(row) {
  if (!row.totalCount) return 0
  return Math.round(((row.successCount || 0) / row.totalCount) * 100)
}
function progressStatus(row) {
  if (row.status === 'COMPLETED') return 'success'
  if (row.status === 'PARTIAL_FAILED') return 'exception'
  return undefined
}
function canEdit(s) {
  return s === 'PENDING'
}

// ============ 新建/编辑表单 ============
const formVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  batchName: '', sourceFloorNum: null, targetFloorNum: null,
  department: '', remark: '', operatorName: '行政管理员', items: []
})
const formRules = {
  batchName: [{ required: true, message: '请输入批次名称', trigger: 'blur' }],
  sourceFloorNum: [{ required: true, message: '请选择迁出楼层', trigger: 'change' }],
  targetFloorNum: [{ required: true, message: '请选择迁入楼层', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, {
    batchName: '', sourceFloorNum: null, targetFloorNum: null,
    department: '', remark: '', operatorName: '行政管理员', items: []
  })
  formRef.value?.clearValidate?.()
}

async function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

async function openEdit(row) {
  detailVisible.value = false
  editingId.value = row.id
  resetForm()
  const res = await getRelocationBatchDetail(row.id)
  const b = res.data
  Object.assign(form, {
    batchName: b.batchName,
    sourceFloorNum: b.sourceFloorNum,
    targetFloorNum: b.targetFloorNum,
    department: b.department || '',
    remark: b.remark || '',
    operatorName: b.operatorName || '行政管理员',
    items: (b.items || []).map((i) => ({
      furnitureId: i.furnitureId,
      furnitureCode: i.furnitureCode,
      _floor: i.snapshotFloorNum,
      snapshotFloorNum: i.snapshotFloorNum,
      snapshotStationCode: i.snapshotStationCode,
      snapshotEmployeeName: i.snapshotEmployeeName,
      snapshotDepartment: i.snapshotDepartment,
      snapshotBindStatus: i.snapshotBindStatus,
      targetStationCode: i.targetStationCode,
      targetEmployeeName: i.targetEmployeeName,
      targetDepartment: i.targetDepartment
    }))
  })
  formVisible.value = true
}

function isDupStation(code) {
  if (!code) return false
  const upper = code.trim().toUpperCase()
  return form.items.filter((i) => (i.targetStationCode || '').trim().toUpperCase() === upper).length > 1
}

function stationFloorWarn(row) {
  if (!form.targetFloorNum || !row.targetStationCode) return false
  const m = /^G(\d+)/i.exec(row.targetStationCode.trim())
  return !m || Number(m[1]) !== form.targetFloorNum
}

function onStationInput(row) {
  // 工位变化时，若使用人/部门为空则不动；楼层不符实时提示
  void row
}

async function saveBatch() {
  await formRef.value.validate().catch(() => {
    throw new Error('请完善表单')
  })
  if (form.items.length === 0) {
    ElMessage.error('请至少添加一件家具')
    return
  }
  if (form.sourceFloorNum === form.targetFloorNum) {
    ElMessage.error('迁出楼层与迁入楼层不能相同')
    return
  }
  for (const it of form.items) {
    if (!it.targetStationCode || !it.targetEmployeeName || !it.targetDepartment) {
      ElMessage.error(`家具 ${it.furnitureCode} 的目标工位/使用人/部门不能为空`)
      return
    }
    if (stationFloorWarn(it)) {
      ElMessage.error(`家具 ${it.furnitureCode} 的目标工位 ${it.targetStationCode} 不属于迁入楼层 ${form.targetFloorNum} 层`)
      return
    }
  }
  const stationSet = new Set(form.items.map((i) => i.targetStationCode.trim().toUpperCase()))
  if (stationSet.size !== form.items.length) {
    ElMessage.error('目标工位在批次内重复')
    return
  }
  const payload = {
    batchName: form.batchName,
    sourceFloorNum: form.sourceFloorNum,
    targetFloorNum: form.targetFloorNum,
    department: form.department || null,
    remark: form.remark || null,
    operatorName: form.operatorName,
    items: form.items.map((i) => ({
      furnitureId: i.furnitureId,
      targetStationCode: i.targetStationCode.trim(),
      targetEmployeeName: i.targetEmployeeName.trim(),
      targetDepartment: i.targetDepartment.trim()
    }))
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateRelocationBatch(editingId.value, payload)
      ElMessage.success('批次已更新，仍为待确认状态')
    } else {
      await createRelocationBatch(payload)
      ElMessage.success('批次已创建（待确认），原绑定快照已保留')
    }
    formVisible.value = false
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

// ============ 家具选择 ============
const pickerVisible = ref(false)
const pickerList = ref([])
const pickerSelection = ref([])
const pickerKeyword = ref('')
const pickerFloor = ref(null)
const pickerTableRef = ref()

async function openFurniturePicker() {
  if (!form.sourceFloorNum) {
    ElMessage.warning('请先选择迁出楼层')
    return
  }
  pickerFloor.value = form.sourceFloorNum
  pickerKeyword.value = ''
  pickerSelection.value = []
  pickerVisible.value = true
  await loadPicker()
  // 回显已在本批次中的选中
  pickerTableRef.value?.clearSelection?.()
  pickerList.value.forEach((f) => {
    if (isInForm(f.id)) {
      // 已在本批次内的不强制选中，避免重复加入
    }
  })
}

async function loadPicker() {
  const res = await listFurniture({
    keyword: pickerKeyword.value || undefined,
    floorNum: pickerFloor.value ?? undefined
  })
  // 已在表单中的家具标记；是否锁定由后端创建时强校验，这里前端不掌握全局锁信息
  pickerList.value = (res.data || []).map((f) => ({ ...f, _locked: false }))
}

function onPickerSelection(rows) {
  pickerSelection.value = rows.filter((r) => !isInForm(r.id))
}

function isInForm(fid) {
  return form.items.some((i) => i.furnitureId === fid)
}

function pickerRowClass({ row }) {
  if (row._locked) return 'row-locked'
  if (isInForm(row.id)) return 'row-in-form'
  return ''
}

function confirmPicker() {
  const add = pickerSelection.value
  for (const f of add) {
    if (isInForm(f.id)) continue
    form.items.push({
      furnitureId: f.id,
      furnitureCode: f.furnitureCode,
      _floor: f.floorNum,
      snapshotFloorNum: f.floorNum,
      snapshotStationCode: f.stationCode,
      snapshotEmployeeName: f.employeeName,
      snapshotDepartment: f.department,
      snapshotBindStatus: f.bindStatus,
      targetStationCode: f.stationCode ? f.stationCode.replace(/^G\d+/i, 'G' + form.targetFloorNum) : '',
      targetEmployeeName: f.employeeName || '',
      targetDepartment: form.department || f.department || ''
    })
  }
  ElMessage.success(`已加入 ${add.length} 件家具`)
  pickerVisible.value = false
}

// ============ 详情 ============
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = reactive({})
const validating = ref(false)
const confirming = ref(false)
const executing = ref(false)
const withdrawing = ref(false)
const validation = ref(null)
const detailTab = ref('items')
const itemFilter = ref('ALL')
let pollTimer = null

const filteredItems = computed(() => {
  if (!detail.items) return []
  if (itemFilter.value === 'ALL') return detail.items
  return detail.items.filter((i) => i.status === itemFilter.value)
})

async function openDetail(row) {
  detailVisible.value = true
  detailTab.value = row.status === 'PARTIAL_FAILED' ? 'items' : 'items'
  itemFilter.value = row.status === 'PARTIAL_FAILED' ? 'FAILED' : 'ALL'
  validation.value = null
  await loadDetail(row.id)
  // 待确认批次进入时自动带一次校验结果，便于直接看逐项问题
  if (detail.status === 'PENDING') {
    runValidate(false).catch(() => {})
  }
  startPolling()
}

async function loadDetail(id) {
  const targetId = id || detail.id
  if (!targetId) return
  detailLoading.value = true
  try {
    const res = await getRelocationBatchDetail(targetId)
    Object.keys(detail).forEach((k) => delete detail[k])
    Object.assign(detail, res.data)
  } finally {
    detailLoading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!detailVisible.value) return
    await loadDetail()
    if (!['RUNNING', 'PARTIAL_FAILED'].includes(detail.status)) {
      stopPolling()
    }
  }, 3000)
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}
onBeforeUnmount(stopPolling)

async function runValidate(andConfirm) {
  const doCall = andConfirm ? confirmRelocationBatch : validateRelocationBatch
  const flag = andConfirm ? confirming : validating
  flag.value = true
  try {
    const res = await doCall(detail.id, form.operatorName || '行政管理员')
    validation.value = res.data
    if (andConfirm) {
      if (res.data.passable) {
        ElMessage.success('校验通过，批次已进入可执行状态')
      } else {
        ElMessage.error(`存在 ${res.data.problemCount} 项冲突，不能确认，请按校验明细处理`)
      }
    } else {
      ElMessage[res.data.passable ? 'success' : 'warning'](
        res.data.passable ? '逐项校验通过' : `发现 ${res.data.problemCount} 项冲突`
      )
    }
    await loadDetail()
  } finally {
    flag.value = false
  }
}

async function doWithdraw() {
  await ElMessageBox.confirm('撤回确认后批次回到待确认，可调整内容并重新校验；原创建快照仍会保留。确认撤回？', '撤回确认', {
    type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  withdrawing.value = true
  try {
    await withdrawRelocationBatch(detail.id, '行政管理员')
    ElMessage.success('已撤回确认')
    validation.value = null
    await loadDetail()
  } finally {
    withdrawing.value = false
  }
}

async function doCancel() {
  await ElMessageBox.confirm('撤销后批次不可执行，在途家具占用将释放；已撤销批次的条目与快照仍可查看。确认撤销？', '撤销批次', {
    type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  await cancelRelocationBatch(detail.id, '行政管理员')
  ElMessage.success('批次已撤销')
  validation.value = null
  await loadDetail()
  loadList()
}

async function doExecute() {
  const isRetry = detail.status === 'PARTIAL_FAILED'
  await ElMessageBox.confirm(
    isRetry
      ? `将按最新数据重新校验并只重试 ${detail.failCount} 个失败项；已成功的项不会重复搬迁、不会重复生成台账。确认重试？`
      : '确认开始执行搬迁？系统将逐件写入绑定关系并在台账中生成本批次记录，成功条目不会回滚。',
    isRetry ? '重试失败项' : '开始执行',
    { type: isRetry ? 'warning' : 'success', confirmButtonText: isRetry ? '重试失败项' : '开始执行' }
  ).catch(() => { throw new Error('cancel') })
  executing.value = true
  try {
    // 执行可能较慢，但服务端有幂等保护；超时/重复点击可安全重试
    const res = await executeRelocationBatch(detail.id, '行政管理员')
    const b = res.data
    Object.keys(detail).forEach((k) => delete detail[k])
    Object.assign(detail, b)
    if (b.status === 'COMPLETED') {
      ElMessage.success(`全部 ${b.totalCount} 项搬迁完成，台账已生成`)
      stopPolling()
    } else if (b.status === 'PARTIAL_FAILED') {
      ElMessage.warning(`执行结束：成功 ${b.successCount} 项，失败 ${b.failCount} 项，可重试失败项`)
    } else {
      ElMessage.success('批次状态已更新')
    }
    itemFilter.value = b.status === 'PARTIAL_FAILED' ? 'FAILED' : 'ALL'
    loadList()
  } catch (e) {
    // 超时等情况下刷新服务端真实状态
    await loadDetail()
  } finally {
    executing.value = false
  }
}

function goRecords(row) {
  detailVisible.value = false
  router.push({ path: '/records', query: { batchNo: detail.batchNo, code: row.furnitureCode } })
}

onMounted(async () => {
  const fRes = await getAllFloors()
  floors.value = fRes.data || []
  await loadList()
})
</script>

<style scoped lang="scss">
.mb-20 { margin-bottom: 20px; }
.mt-20 { margin-top: 20px; }
.text-muted { color: #909399; }
.text-danger { color: #F56C6C; }

.stat-card {
  .stat-label { font-size: 13px; color: #909399; }
  .stat-value { font-size: 26px; font-weight: 700; margin-top: 4px; }
}

.flow-arrow { margin: 0 6px; vertical-align: middle; color: #c0c4cc; }

.pagination-wrap { display: flex; justify-content: flex-end; }

.drawer-body { padding: 0 4px 80px; }

.drawer-footer {
  position: sticky;
  bottom: 0;
  background: #fff;
  padding: 12px 0;
  text-align: right;
  border-top: 1px solid #ebeef5;
  margin-top: 16px;
}

.pick-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0 12px;
}
.pick-tip { color: #909399; font-size: 12px; }

.snap-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}

.station-dup :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #F56C6C inset;
}

.picker-filter {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

:deep(.row-locked) { background-color: #fef0f0 !important; }
:deep(.row-in-form) { background-color: #fdf6ec !important; }

.action-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px 16px;
  background: #f8fafc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.table-toolbar { margin-bottom: 10px; }

.fail-reason {
  margin-left: 8px;
  font-size: 12px;
  color: #F56C6C;
  cursor: help;
}
.result-note {
  font-size: 12px;
  color: #67C23A;
  margin-top: 2px;
  line-height: 1.4;
}
</style>
