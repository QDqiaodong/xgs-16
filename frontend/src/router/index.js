import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据总览', icon: 'DataAnalysis' }
      },
      {
        path: 'floors',
        name: 'Floors',
        component: () => import('@/views/FloorGroups.vue'),
        meta: { title: '楼层分组视图', icon: 'OfficeBuilding' }
      },
      {
        path: 'furniture',
        name: 'Furniture',
        component: () => import('@/views/FurnitureList.vue'),
        meta: { title: '桌椅档案管理', icon: 'Files' }
      },
      {
        path: 'bind',
        name: 'BindManage',
        component: () => import('@/views/BindManage.vue'),
        meta: { title: '工位绑定管理', icon: 'Link' }
      },
      {
        path: 'relocation',
        name: 'RelocationDesk',
        component: () => import('@/views/RelocationDesk.vue'),
        meta: { title: '楼层搬迁交接台', icon: 'Switch' }
      },
      {
        path: 'query',
        name: 'ReverseQuery',
        component: () => import('@/views/ReverseQuery.vue'),
        meta: { title: '工位反向查询', icon: 'Search' }
      },
      {
        path: 'records',
        name: 'Records',
        component: () => import('@/views/BindRecords.vue'),
        meta: { title: '变更台账记录', icon: 'Document' }
      },
      {
        path: 'spec-templates',
        name: 'SpecTemplates',
        component: () => import('@/views/SpecTemplates.vue'),
        meta: { title: '规格模板管理', icon: 'Collection' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const title = to.meta?.title
  if (title) {
    document.title = `${title} - 写字楼办公桌椅工位绑定管理系统`
  }
  next()
})

export default router
