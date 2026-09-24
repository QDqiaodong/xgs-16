<template>
  <el-container class="main-layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="28" color="#fff"><OfficeBuilding /></el-icon>
        <span class="logo-text">工位管理</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        class="menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag type="success" effect="plain" size="small">系统运行中</el-tag>
          <span class="admin-name">行政管理员</span>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  DataAnalysis, OfficeBuilding, Files, Link, Search, Document, Collection, Switch
} from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '数据总览')

const menuItems = [
  { path: '/dashboard', title: '数据总览', icon: DataAnalysis },
  { path: '/floors', title: '楼层分组视图', icon: OfficeBuilding },
  { path: '/furniture', title: '桌椅档案管理', icon: Files },
  { path: '/bind', title: '工位绑定管理', icon: Link },
  { path: '/relocation', title: '楼层搬迁交接台', icon: Switch },
  { path: '/query', title: '工位反向查询', icon: Search },
  { path: '/records', title: '变更台账记录', icon: Document },
  { path: '/spec-templates', title: '规格模板管理', icon: Collection }
]
</script>

<style scoped lang="scss">
.main-layout {
  height: 100vh;
}

.aside {
  background: #1f2d3d;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #263445;
  border-bottom: 1px solid #1a2533;

  .logo-text {
    color: #fff;
    font-size: 16px;
    font-weight: 600;
    letter-spacing: 1px;
  }
}

.menu {
  border: none;
  flex: 1;
  overflow-y: auto;
}

:deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
}

.header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-name {
  color: #606266;
  font-size: 14px;
}

.main {
  background: #f5f7fa;
  padding: 20px;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
