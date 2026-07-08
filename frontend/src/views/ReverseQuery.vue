<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">工位反向查询</h2>
        <div class="page-subtitle">输入工位编号，快速反查对应桌椅档案</div>
      </div>
    </div>

    <el-card class="card-shadow mb-20 search-card" :body-style="{ padding: '30px' }">
      <div class="search-wrap">
        <el-input
          ref="searchInputRef"
          v-model="stationKeyword"
          size="large"
          placeholder="请输入工位编号，例如: G2-A001"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon :size="18"><Search /></el-icon>
          </template>
          <template #append>
            <el-button size="large" type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>查询
            </el-button>
          </template>
        </el-input>
      </div>

      <div class="quick-tags mt-20">
        <span class="tag-label">热门工位：</span>
        <el-tag
          v-for="tag in hotTags"
          :key="tag"
          class="hot-tag"
          effect="plain"
          @click="quickSearch(tag)"
        >
          {{ tag }}
        </el-tag>
      </div>
    </el-card>

    <el-card v-if="searched" class="card-shadow mb-20" :body-style="{ padding: '20px' }">
      <template #header>
        <div class="flex-between">
          <span>查询结果：工位 <el-tag type="primary" effect="dark" size="default">{{ stationKeyword }}</el-tag></span>
          <span class="text-muted" style="font-size: 13px;">找到 {{ resultList.length }} 条关联家具</span>
        </div>
      </template>

      <el-empty v-if="resultList.length === 0" description="该工位暂无绑定的桌椅档案" />

      <el-row v-else :gutter="16">
        <el-col
          v-for="item in resultList"
          :key="item.id"
          :xs="24" :sm="12" :md="8" :lg="6"
          class="mb-16"
        >
          <div class="result-card">
            <div class="rc-image">
              <img v-if="item.imageUrl" :src="item.imageUrl" />
              <div v-else class="rc-image-placeholder">
                <el-icon :size="56"><Box /></el-icon>
                <div class="rc-type-label">{{ item.furnitureType }}</div>
              </div>
              <div class="rc-status-tag" :class="item.bindStatus === 1 ? 'bound' : 'unbound'">
                {{ item.bindStatus === 1 ? '使用中' : '闲置' }}
              </div>
            </div>
            <div class="rc-body">
              <div class="rc-code">{{ item.furnitureCode }}</div>
              <div class="rc-title">{{ item.styleName || item.furnitureType }}</div>
              <div class="rc-detail-row">
                <span class="rc-label">品牌</span>
                <span>{{ item.brand || '—' }}</span>
              </div>
              <div class="rc-detail-row">
                <span class="rc-label">尺寸</span>
                <span>{{ item.sizeSpec || '—' }}</span>
              </div>
              <div class="rc-detail-row">
                <span class="rc-label">价格</span>
                <span>{{ item.price ? '¥' + item.price : '—' }}</span>
              </div>
              <div class="rc-detail-row">
                <span class="rc-label">楼层</span>
                <span>{{ item.floorNum }}层 {{ item.areaName || '' }}</span>
              </div>
              <div class="rc-detail-row" v-if="item.bindStatus === 1">
                <span class="rc-label">使用人</span>
                <span>{{ item.employeeName || '—' }} {{ item.department ? '(' + item.department + ')' : '' }}</span>
              </div>
              <div class="rc-footer">
                <el-button size="small" type="primary" plain @click="viewRecords(item)">
                  变更台账
                </el-button>
                <el-button size="small" @click="goDetail(item)">档案详情</el-button>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="searched && resultList.length > 0" class="card-shadow" :body-style="{ padding: '20px' }">
      <template #header>
        <span>工位使用分布信息</span>
      </template>
      <el-descriptions :column="3" border size="default">
        <el-descriptions-item label="工位编号">
          <el-tag type="primary">{{ stationKeyword }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联家具数">{{ resultList.length }} 件</el-descriptions-item>
        <el-descriptions-item label="所在楼层">
          {{ [...new Set(resultList.map(r => r.floorNum))].join(', ') }}层
        </el-descriptions-item>
        <el-descriptions-item label="使用人">
          {{ resultList[0]?.employeeName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="所属部门">
          {{ resultList[0]?.department || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="绑定状态">
          <el-tag :type="resultList.some(r => r.bindStatus === 1) ? 'success' : 'info'" size="small">
            {{ resultList.some(r => r.bindStatus === 1) ? '已绑定' : '未绑定' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, Box } from '@element-plus/icons-vue'
import { getFurnitureByStationCode, listFurniture } from '@/api'

const router = useRouter()
const route = useRoute()

const searchInputRef = ref(null)
const stationKeyword = ref('')
const searched = ref(false)
const resultList = ref([])

const hotTags = ref(['G1-A001', 'G2-A001', 'G2-A002', 'G3-A001', 'G5-EX01'])

async function handleSearch() {
  if (!stationKeyword.value.trim()) {
    return
  }
  searched.value = true
  try {
    const res = await getFurnitureByStationCode(stationKeyword.value.trim())
    resultList.value = res.data || []
  } catch (e) {
    resultList.value = []
  }
}

function quickSearch(tag) {
  stationKeyword.value = tag
  handleSearch()
}

function viewRecords(item) {
  router.push({ path: '/records', query: { code: item.furnitureCode } })
}

function goDetail(item) {
  router.push({ path: '/furniture', query: { kw: item.furnitureCode } })
}

onMounted(async () => {
  await nextTick()
  searchInputRef.value?.focus?.()

  const st = route.query.station
  if (st) {
    stationKeyword.value = st
    handleSearch()
    return
  }

  try {
    const res = await listFurniture({ bindStatus: 1 })
    const list = res.data || []
    const tags = [...new Set(list.slice(0, 30).map(r => r.stationCode).filter(Boolean))].slice(0, 6)
    if (tags.length > 0) hotTags.value = tags
  } catch (e) {}
})
</script>

<style scoped lang="scss">
.search-card {
  background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);
  border: 1px solid #ebeef5;
}

.search-wrap {
  max-width: 720px;
  margin: 0 auto;
}

.search-input :deep(.el-input__wrapper) {
  padding: 6px 10px;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
  border-radius: 10px;
}

.quick-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 720px;
  margin: 0 auto;
}

.tag-label {
  color: #909399;
  font-size: 13px;
}

.hot-tag {
  cursor: pointer;
  transition: all 0.2s;
  &:hover {
    color: #409EFF;
    border-color: #409EFF;
  }
}

.mb-16 {
  margin-bottom: 16px;
}

.result-card {
  border: 1px solid #ebeef5;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  transition: all 0.25s;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0,0,0,0.08);
    border-color: #409EFF;
  }
}

.rc-image {
  height: 160px;
  position: relative;
  background: #f5f7fa;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.rc-image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  gap: 6px;
}

.rc-type-label {
  font-size: 14px;
  font-weight: 600;
}

.rc-status-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;

  &.bound { background: #67C23A; }
  &.unbound { background: #909399; }
}

.rc-body {
  padding: 14px 16px;
}

.rc-code {
  font-size: 14px;
  font-weight: 700;
  color: #409EFF;
  letter-spacing: 0.5px;
}

.rc-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 4px 0 10px;
}

.rc-detail-row {
  display: flex;
  font-size: 13px;
  color: #606266;
  padding: 3px 0;
}

.rc-label {
  width: 60px;
  flex-shrink: 0;
  color: #909399;
}

.rc-footer {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f2f6fc;
  display: flex;
  gap: 8px;
}
</style>
