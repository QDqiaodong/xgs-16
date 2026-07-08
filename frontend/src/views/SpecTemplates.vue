<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">规格模板管理</h2>
        <div class="page-subtitle">基于 Redis 哈希缓存的桌椅规格参数模板（过期时间30天）</div>
      </div>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>新建模板
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col
        v-for="tpl in templateList"
        :key="tpl.name"
        :xs="24" :sm="12" :md="8" :lg="6"
        class="mb-16"
      >
        <el-card class="tpl-card card-shadow" :body-style="{ padding: '0' }">
          <div class="tpl-header">
            <el-icon :size="20"><Collection /></el-icon>
            <span class="tpl-name">{{ tpl.name }}</span>
          </div>
          <div class="tpl-body">
            <div
              v-for="(val, key) in tpl.spec"
              :key="key"
              class="tpl-item"
            >
              <span class="tpl-item-key">{{ key }}</span>
              <span class="tpl-item-val">{{ val }}</span>
            </div>
            <el-empty v-if="!tpl.spec || Object.keys(tpl.spec).length === 0" :image-size="60" description="无规格数据" />
          </div>
          <div class="tpl-footer">
            <el-button size="small" type="primary" link @click="handleApply(tpl)">
              <el-icon><Check /></el-icon>套用
            </el-button>
            <el-button size="small" type="warning" link @click="handleEdit(tpl)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(tpl)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty
      v-if="!loading && templateList.length === 0"
      description="暂无规格模板，点击右上角创建"
    />

    <el-dialog v-model="formVisible" :title="isEdit ? '编辑规格模板' : '新建规格模板'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" :disabled="isEdit" placeholder="如:标准职员桌-1.4米" />
        </el-form-item>
        <el-form-item label="家具类型">
          <el-select v-model="form.spec.furnitureType" placeholder="选择类型" style="width:100%">
            <el-option label="办公桌" value="办公桌" />
            <el-option label="办公椅" value="办公椅" />
            <el-option label="会议桌" value="会议桌" />
            <el-option label="培训桌" value="培训桌" />
            <el-option label="老板桌" value="老板桌" />
          </el-select>
        </el-form-item>
        <el-form-item label="款式名称">
          <el-input v-model="form.spec.styleName" />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.spec.brand" />
        </el-form-item>
        <el-form-item label="尺寸规格">
          <el-input v-model="form.spec.sizeSpec" placeholder="如:1400x700x750" />
        </el-form-item>
        <el-form-item label="自定义规格">
          <el-input
            v-model="customSpec"
            type="textarea"
            :rows="3"
            placeholder="每行一条 键:值 格式，例如：&#10;材质:高密度板&#10;颜色:胡桃木色"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确认保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Collection, Check, Edit, Delete
} from '@element-plus/icons-vue'
import {
  saveSpecTemplate, getAllSpecTemplates, deleteSpecTemplate
} from '@/api'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const templateList = ref([])
const formVisible = ref(false)
const isEdit = ref(false)
const customSpec = ref('')

const form = reactive({
  name: '',
  spec: {
    furnitureType: '',
    styleName: '',
    brand: '',
    sizeSpec: ''
  }
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAllSpecTemplates()
    templateList.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  form.name = ''
  form.spec = { furnitureType: '', styleName: '', brand: '', sizeSpec: '' }
  customSpec.value = ''
  formVisible.value = true
}

function handleEdit(tpl) {
  isEdit.value = true
  form.name = tpl.name
  const spec = tpl.spec || {}
  form.spec = {
    furnitureType: spec.furnitureType || '',
    styleName: spec.styleName || '',
    brand: spec.brand || '',
    sizeSpec: spec.sizeSpec || ''
  }
  const extras = []
  Object.entries(spec).forEach(([k, v]) => {
    if (!['furnitureType', 'styleName', 'brand', 'sizeSpec'].includes(k)) {
      extras.push(`${k}:${v}`)
    }
  })
  customSpec.value = extras.join('\n')
  formVisible.value = true
}

async function submitForm() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  const spec = { ...form.spec }
  if (customSpec.value.trim()) {
    customSpec.value.split('\n').forEach(line => {
      const idx = line.indexOf(':')
      if (idx > 0) {
        const k = line.substring(0, idx).trim()
        const v = line.substring(idx + 1).trim()
        if (k && v) spec[k] = v
      }
    })
  }
  submitting.value = true
  try {
    await saveSpecTemplate(form.name.trim(), spec)
    ElMessage.success(isEdit.value ? '模板已更新' : '模板已创建并缓存至 Redis')
    formVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(tpl) {
  try {
    await ElMessageBox.confirm(`确定删除规格模板「${tpl.name}」吗？`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteSpecTemplate(tpl.name)
  ElMessage.success('模板已删除')
  loadList()
}

function handleApply(tpl) {
  ElMessage.success(`规格模板「${tpl.name}」已复制，可前往【桌椅档案管理】新建时使用`)
  router.push('/furniture')
}

onMounted(async () => {
  await loadList()
  if (templateList.value.length === 0) {
    const presets = [
      {
        name: '标准职员桌-1.4米',
        spec: { furnitureType: '办公桌', styleName: '标准职员桌', brand: '震旦', sizeSpec: '1400x700x750', 材质: 'E1级密度板' }
      },
      {
        name: '人体工学主管椅',
        spec: { furnitureType: '办公椅', styleName: '人体工学椅', brand: '联友', sizeSpec: '660x680x1150', 材质: '高背网布' }
      },
      {
        name: 'L型主管桌-1.6米',
        spec: { furnitureType: '办公桌', styleName: 'L型主管桌', brand: '震旦', sizeSpec: '1600x800x750', 颜色: '胡桃木色' }
      }
    ]
    for (const p of presets) {
      try { await saveSpecTemplate(p.name, p.spec) } catch (e) {}
    }
    loadList()
  }
})
</script>

<style scoped lang="scss">
.mb-16 {
  margin-bottom: 16px;
}

.tpl-card {
  border: none;
  border-radius: 12px;
  overflow: hidden;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-3px);
  }
}

.tpl-header {
  background: linear-gradient(135deg, #409EFF 0%, #36cfc9 100%);
  color: #fff;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.tpl-name {
  font-size: 15px;
  font-weight: 600;
}

.tpl-body {
  padding: 14px 18px;
  min-height: 140px;
  max-height: 240px;
  overflow-y: auto;
}

.tpl-item {
  display: flex;
  padding: 5px 0;
  border-bottom: 1px dashed #f2f6fc;
  font-size: 13px;

  &:last-child {
    border-bottom: none;
  }
}

.tpl-item-key {
  width: 90px;
  flex-shrink: 0;
  color: #909399;
}

.tpl-item-val {
  color: #303133;
  flex: 1;
  word-break: break-all;
}

.tpl-footer {
  padding: 10px 18px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
</style>
