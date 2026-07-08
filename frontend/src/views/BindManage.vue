<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">工位绑定管理</h2>
        <div class="page-subtitle">批量处理工位绑定解绑与人员调整</div>
      </div>
      <div>
        <el-button type="danger" plain @click="showBatchBind = true">
          <el-icon><Link /></el-icon>批量绑定
        </el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card class="card-shadow" :body-style="{ padding: '16px 20px' }">
          <template #header>
            <div class="card-header">
              <span><el-icon style="color: #F56C6C;"><Warning /></el-icon> 待绑定/已解绑清单</span>
              <el-tag type="info" size="small">共 {{ unboundList.length }} 项</el-tag>
            </div>
          </template>

          <el-input
            v-model="unboundSearch"
            placeholder="搜索桌椅编号/款式"
            class="mb-10"
            clearable
            :prefix-icon="Search"
          />

          <div class="list-wrap">
            <el-empty v-if="filteredUnbound.length === 0" description="暂无未绑定资产" />
            <div
              v-for="item in filteredUnbound"
              :key="item.id"
              class="furniture-card unbound"
              @click="selectBindItem(item)"
            >
              <div class="fc-header">
                <span class="fc-code">{{ item.furnitureCode }}</span>
                <el-tag type="info" size="small" effect="plain">未绑定</el-tag>
              </div>
              <div class="fc-body">
                <span class="fc-type">{{ item.furnitureType }}</span>
                <span class="fc-name">{{ item.styleName || item.brand }}</span>
              </div>
              <div class="fc-footer">
                <span>📍 {{ item.floorNum }}层 {{ item.areaName || '' }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="card-shadow" :body-style="{ padding: '16px 20px' }">
          <template #header>
            <div class="card-header">
              <span><el-icon style="color: #67C23A;"><CircleCheck /></el-icon> 已绑定清单</span>
              <el-tag type="success" size="small">共 {{ boundList.length }} 项</el-tag>
            </div>
          </template>

          <el-input
            v-model="boundSearch"
            placeholder="搜索编号/工位/使用人"
            class="mb-10"
            clearable
            :prefix-icon="Search"
          />

          <div class="list-wrap">
            <el-empty v-if="filteredBound.length === 0" description="暂无已绑定资产" />
            <div
              v-for="item in filteredBound"
              :key="item.id"
              class="furniture-card bound"
              @click="selectUnbindItem(item)"
            >
              <div class="fc-header">
                <span class="fc-code">{{ item.furnitureCode }}</span>
                <el-tag type="success" size="small" effect="dark">{{ item.stationCode }}</el-tag>
              </div>
              <div class="fc-body">
                <span class="fc-type">{{ item.furnitureType }}</span>
                <span class="fc-name">{{ item.styleName || item.brand }}</span>
              </div>
              <div class="fc-footer">
                <span class="fc-user">
                  <el-icon><User /></el-icon> {{ item.employeeName || '未分配' }}
                </span>
                <span v-if="item.department" class="fc-dept">{{ item.department }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="bindDialogVisible" title="工位绑定登记" width="520px" destroy-on-close>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="100px">
        <el-form-item label="家具编号">
          <el-input v-model="bindForm.furnitureCode" disabled />
        </el-form-item>
        <el-form-item label="工位编号" prop="stationCode">
          <el-input v-model="bindForm.stationCode" placeholder="如:G3-A001" />
        </el-form-item>
        <el-form-item label="使用人">
          <el-input v-model="bindForm.employeeName" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="bindForm.department" placeholder="部门名称" />
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input v-model="bindForm.operateReason" type="textarea" :rows="2" placeholder="说明原因" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="bindForm.operatorName" placeholder="默认:系统管理员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBind">确认绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="unbindDialogVisible" title="工位解绑确认" width="520px" destroy-on-close>
      <el-alert
        :title="`确定解绑 ${unbindForm.furnitureCode} 与工位 ${unbindForm.oldStationCode}?`"
        type="warning"
        show-icon
        class="mb-20"
      />
      <el-form :model="unbindForm" label-width="100px">
        <el-form-item label="原工位">
          <el-input v-model="unbindForm.oldStationCode" disabled />
        </el-form-item>
        <el-form-item label="原使用人">
          <el-input v-model="unbindForm.oldEmployee" disabled />
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input v-model="unbindForm.operateReason" type="textarea" :rows="2" placeholder="请说明原因" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="unbindForm.operatorName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unbindDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitUnbind">确认解绑</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showBatchBind" title="批量绑定工位" width="600px" destroy-on-close>
      <el-alert type="info" show-icon :closable="false" class="mb-10">
        请按照 家具编号,工位编号,使用人,部门 每行一条，示例：DESK-01F-A003,G1-A003,张三,技术部
      </el-alert>
      <el-input
        v-model="batchText"
        type="textarea"
        :rows="8"
        placeholder="每行一条，逗号分隔&#10;DESK-01F-A003,G1-A003,张三,技术部&#10;CHAIR-01F-A003,G1-A003,张三,技术部"
      />
      <template #footer>
        <el-button @click="showBatchBind = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBatchBind">批量绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Link, Warning, CircleCheck, User } from '@element-plus/icons-vue'
import { listFurniture, bindStation, unbindStation } from '@/api'

const loading = ref(false)
const submitting = ref(false)
const allList = ref([])
const unboundSearch = ref('')
const boundSearch = ref('')

const unboundList = computed(() => allList.value.filter(f => f.bindStatus === 0))
const boundList = computed(() => allList.value.filter(f => f.bindStatus === 1))

const filteredUnbound = computed(() => {
  const kw = unboundSearch.value.trim().toLowerCase()
  if (!kw) return unboundList.value
  return unboundList.value.filter(f =>
    f.furnitureCode.toLowerCase().includes(kw) ||
    (f.styleName && f.styleName.toLowerCase().includes(kw))
  )
})

const filteredBound = computed(() => {
  const kw = boundSearch.value.trim().toLowerCase()
  if (!kw) return boundList.value
  return boundList.value.filter(f =>
    f.furnitureCode.toLowerCase().includes(kw) ||
    (f.stationCode && f.stationCode.toLowerCase().includes(kw)) ||
    (f.employeeName && f.employeeName.toLowerCase().includes(kw))
  )
})

const bindDialogVisible = ref(false)
const bindFormRef = ref(null)
const bindForm = reactive({
  furnitureId: null,
  furnitureCode: '',
  stationCode: '',
  employeeName: '',
  department: '',
  operateReason: '',
  operatorName: ''
})
const bindRules = {
  stationCode: [{ required: true, message: '请输入工位编号', trigger: 'blur' }]
}

const unbindDialogVisible = ref(false)
const unbindForm = reactive({
  furnitureId: null,
  furnitureCode: '',
  oldStationCode: '',
  oldEmployee: '',
  operateReason: '',
  operatorName: ''
})

const showBatchBind = ref(false)
const batchText = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await listFurniture({})
    allList.value = res.data || []
  } finally {
    loading.value = false
  }
}

function selectBindItem(item) {
  Object.assign(bindForm, {
    furnitureId: item.id,
    furnitureCode: item.furnitureCode,
    stationCode: '',
    employeeName: '',
    department: '',
    operateReason: '',
    operatorName: ''
  })
  bindDialogVisible.value = true
}

function selectUnbindItem(item) {
  Object.assign(unbindForm, {
    furnitureId: item.id,
    furnitureCode: item.furnitureCode,
    oldStationCode: item.stationCode,
    oldEmployee: item.employeeName || '—',
    operateReason: '',
    operatorName: ''
  })
  unbindDialogVisible.value = true
}

async function submitBind() {
  await bindFormRef.value.validate()
  submitting.value = true
  try {
    await bindStation({ ...bindForm })
    ElMessage.success(`绑定成功：${bindForm.stationCode}`)
    bindDialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function submitUnbind() {
  submitting.value = true
  try {
    await unbindStation({
      furnitureId: unbindForm.furnitureId,
      operateReason: unbindForm.operateReason,
      operatorName: unbindForm.operatorName || '系统管理员'
    })
    ElMessage.success('解绑成功，已生成变更记录')
    unbindDialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function submitBatchBind() {
  const lines = batchText.value.trim().split('\n').filter(l => l.trim())
  if (lines.length === 0) {
    ElMessage.warning('请输入绑定数据')
    return
  }
  submitting.value = true
  let success = 0
  let fail = 0
  for (const line of lines) {
    const parts = line.split(',').map(p => p.trim())
    if (parts.length < 2) { fail++; continue }
    const [code, station, emp, dept] = parts
    const item = allList.value.find(f => f.furnitureCode === code)
    if (!item) { fail++; continue }
    try {
      await bindStation({
        furnitureId: item.id,
        stationCode: station,
        employeeName: emp,
        department: dept,
        operatorName: '批量操作'
      })
      success++
    } catch (e) {
      fail++
    }
  }
  submitting.value = false
  ElMessage.info(`批量完成：成功 ${success} 条，失败 ${fail} 条`)
  showBatchBind.value = false
  batchText.value = ''
  loadData()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.list-wrap {
  max-height: calc(100vh - 360px);
  overflow-y: auto;
  padding-right: 4px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 2px;
  }
}

.furniture-card {
  padding: 12px 14px;
  border-radius: 10px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid;

  &.unbound {
    background: #fafafa;
    border-color: #ebeef5;
    &:hover {
      background: #ecf5ff;
      border-color: #409EFF;
    }
  }

  &.bound {
    background: #f0f9eb;
    border-color: #e1f3d8;
    &:hover {
      background: #e1f3d8;
      border-color: #67C23A;
    }
  }
}

.fc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.fc-code {
  font-weight: 700;
  color: #303133;
}

.fc-body {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}

.fc-type {
  background: #f4f4f5;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.fc-name {
  color: #909399;
}

.fc-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.fc-user, .fc-dept {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
