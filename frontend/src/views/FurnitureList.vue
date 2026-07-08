<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">桌椅档案管理</h2>
        <div class="page-subtitle">办公桌椅基础信息录入、编辑、删除</div>
      </div>
      <div>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新建档案
        </el-button>
      </div>
    </div>

    <el-card class="card-shadow mb-20" :body-style="{ padding: '16px 20px' }">
      <el-form :inline="true" :model="filters" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="编号/款式/工位/使用人" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="filters.floorNum" placeholder="全部楼层" clearable style="width: 140px">
            <el-option v-for="f in allFloors" :key="f" :label="f + '层'" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.bindStatus" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="已绑定" :value="1" />
            <el-option label="未绑定" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.furnitureType" placeholder="全部类型" clearable style="width: 120px">
            <el-option label="办公桌" value="办公桌" />
            <el-option label="办公椅" value="办公椅" />
            <el-option label="会议桌" value="会议桌" />
            <el-option label="培训桌" value="培训桌" />
            <el-option label="老板桌" value="老板桌" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card-shadow" :body-style="{ padding: '16px 20px' }">
      <el-table :data="tableData" stripe v-loading="loading" style="width: 100%">
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="furnitureCode" label="桌椅编号" width="160" fixed="left">
          <template #default="{ row }">
            <span class="text-primary" style="font-weight: 600;">{{ row.furnitureCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.furnitureType)">{{ row.furnitureType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="styleName" label="款式" width="140" show-overflow-tooltip />
        <el-table-column prop="brand" label="品牌" width="110" show-overflow-tooltip />
        <el-table-column prop="sizeSpec" label="尺寸规格" width="140" show-overflow-tooltip />
        <el-table-column label="价格" width="110" align="right">
          <template #default="{ row }">
            {{ row.price ? '¥' + row.price : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="楼层区域" width="120" align="center">
          <template #default="{ row }">
            {{ row.floorNum }}层<small class="text-muted">{{ row.areaName ? '·' + row.areaName : '' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="工位/使用人" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.bindStatus === 1">
              <el-tag type="success" effect="light" size="small">{{ row.stationCode }}</el-tag>
              <span class="ml-6" style="font-size: 13px;">
                {{ row.employeeName || '—' }}
                <small class="text-muted" v-if="row.department">({{ row.department }})</small>
              </span>
            </div>
            <el-tag v-else type="info" size="small" effect="light">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="success" link @click="handleBind(row)">
              {{ row.bindStatus === 1 ? '重新绑定' : '绑定工位' }}
            </el-button>
            <el-button size="small" type="warning" link v-if="row.bindStatus === 1" @click="handleUnbind(row)">
              解绑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap mt-20">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑桌椅档案' : '新建桌椅档案'"
      width="720px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
        label-position="right"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="桌椅编号" prop="furnitureCode">
              <el-input v-model="formData.furnitureCode" :disabled="isEdit" placeholder="如:DESK-01F-A001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="家具类型" prop="furnitureType">
              <el-select v-model="formData.furnitureType" placeholder="请选择类型" style="width: 100%">
                <el-option label="办公桌" value="办公桌" />
                <el-option label="办公椅" value="办公椅" />
                <el-option label="会议桌" value="会议桌" />
                <el-option label="培训桌" value="培训桌" />
                <el-option label="老板桌" value="老板桌" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="款式名称">
              <el-input v-model="formData.styleName" placeholder="如:L型主管桌" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="formData.brand" placeholder="如:震旦" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="尺寸规格">
              <el-input v-model="formData.sizeSpec" placeholder="如:1600x800x750" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格">
              <el-input-number
                v-model="formData.price"
                :min="0" :precision="2" :step="100"
                style="width: 100%"
                controls-position="right"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适配楼层" prop="floorNum">
              <el-input-number
                v-model="formData.floorNum"
                :min="1" :max="99"
                style="width: 100%"
                controls-position="right"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域名称">
              <el-input v-model="formData.areaName" placeholder="如:A区开放办公" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格模板">
              <el-select
                v-model="formData.specTemplate"
                placeholder="选择已缓存的规格模板"
                clearable
                style="width: 100%"
                @change="applySpecTemplate"
              >
                <el-option
                  v-for="t in specTemplates"
                  :key="t.name"
                  :label="t.name"
                  :value="t.name"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实拍图上传">
              <el-upload
                :show-file-list="false"
                :before-upload="beforeImageUpload"
                :http-request="handleImageUpload"
                accept="image/*"
              >
                <div class="upload-preview">
                  <img v-if="formData.imageUrl" :src="formData.imageUrl" />
                  <div v-else class="upload-placeholder">
                    <el-icon><Plus /></el-icon>
                    <span>上传图片</span>
                  </div>
                </div>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确认提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" title="工位绑定登记" width="520px" destroy-on-close>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="100px">
        <el-form-item label="家具编号">
          <el-input v-model="bindForm.furnitureCode" disabled />
        </el-form-item>
        <el-form-item label="工位编号" prop="stationCode">
          <el-input v-model="bindForm.stationCode" placeholder="如:G2-A001" />
        </el-form-item>
        <el-form-item label="使用人">
          <el-input v-model="bindForm.employeeName" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-input v-model="bindForm.department" placeholder="部门" />
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input v-model="bindForm.operateReason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="bindForm.operatorName" placeholder="默认系统管理员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBind">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import {
  pageFurniture, createFurniture, updateFurniture, deleteFurniture,
  bindStation, unbindStation, getAllFloors, getAllSpecTemplates,
  uploadImage, getFurnitureById
} from '@/api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const allFloors = ref([])
const specTemplates = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const filters = reactive({
  keyword: '',
  floorNum: null,
  bindStatus: null,
  furnitureType: ''
})

const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null,
  furnitureCode: '',
  furnitureType: '',
  styleName: '',
  brand: '',
  price: null,
  sizeSpec: '',
  floorNum: 1,
  areaName: '',
  specTemplate: '',
  imageUrl: '',
  remark: ''
})

const formRules = {
  furnitureCode: [{ required: true, message: '请输入桌椅编号', trigger: 'blur' }],
  furnitureType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  floorNum: [{ required: true, message: '请填写楼层', trigger: 'blur' }]
}

const bindVisible = ref(false)
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

function typeTag(type) {
  const map = {
    '办公桌': 'primary', '办公椅': 'success', '会议桌': 'warning',
    '培训桌': 'info', '老板桌': 'danger'
  }
  return map[type] || ''
}

async function loadAllFloors() {
  const res = await getAllFloors()
  allFloors.value = res.data || []
}

async function loadSpecTemplates() {
  try {
    const res = await getAllSpecTemplates()
    specTemplates.value = res.data || []
  } catch (e) {}
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageFurniture({
      ...filters,
      furnitureType: filters.furnitureType || undefined,
      page: page.value - 1,
      size: size.value
    })
    tableData.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleReset() {
  filters.keyword = ''
  filters.floorNum = null
  filters.bindStatus = null
  filters.furnitureType = ''
  page.value = 1
  loadData()
}

function handleCreate() {
  isEdit.value = false
  Object.assign(formData, {
    id: null, furnitureCode: '', furnitureType: '', styleName: '', brand: '',
    price: null, sizeSpec: '', floorNum: 1, areaName: '', specTemplate: '',
    imageUrl: '', remark: ''
  })
  formVisible.value = true
}

async function handleEdit(row) {
  isEdit.value = true
  const res = await getFurnitureById(row.id)
  const d = res.data
  Object.assign(formData, {
    id: d.id, furnitureCode: d.furnitureCode, furnitureType: d.furnitureType,
    styleName: d.styleName || '', brand: d.brand || '', price: d.price,
    sizeSpec: d.sizeSpec || '', floorNum: d.floorNum, areaName: d.areaName || '',
    specTemplate: d.specTemplate || '', imageUrl: d.imageUrl || '', remark: d.remark || ''
  })
  formVisible.value = true
}

async function applySpecTemplate(name) {
  if (!name) return
  const tpl = specTemplates.value.find(t => t.name === name)
  if (!tpl) return
  const spec = tpl.spec || {}
  if (spec.sizeSpec) formData.sizeSpec = spec.sizeSpec
  if (spec.styleName) formData.styleName = spec.styleName
  if (spec.brand) formData.brand = spec.brand
  if (spec.furnitureType) formData.furnitureType = spec.furnitureType
  ElMessage.info('已应用规格模板，请检查并调整')
}

function beforeImageUpload(file) {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片不能大于10MB')
    return false
  }
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片')
    return false
  }
  return true
}

async function handleImageUpload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await uploadImage(fd)
    formData.imageUrl = res.data.url
    ElMessage.success('图片上传成功')
  } catch (e) {}
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = { ...formData }
    if (isEdit.value) {
      await updateFurniture(payload)
      ElMessage.success('档案更新成功')
    } else {
      await createFurniture(payload)
      ElMessage.success('档案创建成功')
    }
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleBind(row) {
  Object.assign(bindForm, {
    furnitureId: row.id,
    furnitureCode: row.furnitureCode,
    stationCode: '',
    employeeName: '',
    department: '',
    operateReason: '',
    operatorName: ''
  })
  bindVisible.value = true
}

async function submitBind() {
  await bindFormRef.value.validate()
  submitting.value = true
  try {
    await bindStation({ ...bindForm })
    ElMessage.success('工位绑定成功')
    bindVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleUnbind(row) {
  try {
    await ElMessageBox.confirm(`确定要解绑 ${row.furnitureCode} 与工位 ${row.stationCode} 的绑定关系吗？`, '解绑确认', {
      type: 'warning'
    })
  } catch { return }
  try {
    await unbindStation({ furnitureId: row.id, operatorName: '系统管理员' })
    ElMessage.success('解绑成功')
    loadData()
  } catch (e) {}
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除桌椅 ${row.furnitureCode} 的档案吗？此操作不可恢复`, '删除确认', {
      type: 'error'
    })
  } catch { return }
  await deleteFurniture(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  await loadAllFloors()
  await loadSpecTemplates()
  await loadData()
  if (route.query.floor) {
    filters.floorNum = Number(route.query.floor)
    handleSearch()
  }
})
</script>

<style scoped lang="scss">
.ml-6 {
  margin-left: 6px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.upload-preview {
  width: 100px;
  height: 100px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  position: relative;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.upload-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
