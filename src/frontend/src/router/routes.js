// 路由表：按布局分组，页面级权限通过 meta.roles 控制。
// T9/T10：在 user/ 与 admin/ 布局下挂载全部业务路由（原占位 Index.vue 已不再引用）。
const USER_ROLES = ['SUBMITTER', 'DEVELOPER', 'TESTER', 'ADMIN']

const routes = [
  // 登录页（BlankLayout 外壳）
  {
    path: '/login',
    component: () => import('@/layouts/BlankLayout.vue'),
    meta: { public: true },
    children: [
      {
        path: '',
        name: 'login',
        component: () => import('@/views/Login.vue'),
        meta: { public: true, title: '登录' }
      }
    ]
  },
  // 无权限页
  {
    path: '/403',
    component: () => import('@/layouts/BlankLayout.vue'),
    meta: { public: true },
    children: [
      {
        path: '',
        name: 'forbidden',
        component: () => import('@/views/error/Forbidden.vue'),
        meta: { public: true, title: '无权限' }
      }
    ]
  },
  // 用户界面（UserLayout）：提交者 / 开发 / 测试 / 管理员 可见
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    redirect: '/user',
    meta: { title: 'issueFlow' },
    children: [
      {
        path: 'user',
        name: 'user-dashboard',
        component: () => import('@/views/user/UserDashboard.vue'),
        meta: { title: '工作台', roles: USER_ROLES }
      },
      {
        path: 'user/my-issues',
        name: 'user-issues',
        component: () => import('@/views/user/UserIssueList.vue'),
        meta: { title: '我的问题', roles: USER_ROLES }
      },
      {
        path: 'user/submit-issue',
        name: 'issue-create',
        component: () => import('@/views/user/IssueCreate.vue'),
        meta: { title: '提交问题', roles: USER_ROLES }
      },
      {
        path: 'user/stats',
        name: 'user-stats',
        component: () => import('@/views/user/UserStats.vue'),
        meta: { title: '个人看板', roles: USER_ROLES }
      }
    ]
  },
  // 管理后台（AdminLayout）：仅 ADMIN
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/index',
    meta: { title: '管理后台', roles: ['ADMIN'] },
    children: [
      {
        path: 'index',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '概览', roles: ['ADMIN'] }
      },
      {
        path: 'issues',
        name: 'admin-issues',
        component: () => import('@/views/admin/AdminIssueList.vue'),
        meta: { title: '问题管理', roles: ['ADMIN'] }
      },
      {
        path: 'projects',
        name: 'project-manage',
        component: () => import('@/views/admin/ProjectManage.vue'),
        meta: { title: '项目配置', roles: ['ADMIN'] }
      },
      {
        path: 'modules',
        name: 'module-manage',
        component: () => import('@/views/admin/ModuleManage.vue'),
        meta: { title: '模块配置', roles: ['ADMIN'] }
      },
      {
        path: 'flow-monitor',
        name: 'flow-monitor',
        component: () => import('@/views/admin/FlowMonitor.vue'),
        meta: { title: '流程监控', roles: ['ADMIN'] }
      },
      {
        path: 'system',
        component: () => import('@/views/admin/SystemLayout.vue'),
        redirect: '/admin/system/organizations',
        meta: { title: '系统管理', roles: ['ADMIN'] },
        children: [
          {
            path: 'organizations',
            name: 'organization-manage',
            component: () => import('@/views/admin/OrganizationManage.vue'),
            meta: { title: '组织管理', roles: ['ADMIN'] }
          },
          {
            path: 'menus',
            name: 'menu-manage',
            component: () => import('@/views/admin/MenuManage.vue'),
            meta: { title: '菜单管理', roles: ['ADMIN'] }
          },
          {
            path: 'users',
            name: 'user-manage',
            component: () => import('@/views/admin/UserManage.vue'),
            meta: { title: '用户管理', roles: ['ADMIN'] }
          },
          {
            path: 'roles',
            name: 'role-manage',
            component: () => import('@/views/admin/RoleManage.vue'),
            meta: { title: '角色管理', roles: ['ADMIN'] }
          },
          {
            path: 'settings',
            name: 'system-settings',
            component: () => import('@/views/admin/SystemSettings.vue'),
            meta: { title: '系统设置', roles: ['ADMIN'] }
          }
        ]
      },
      {
        path: 'flow-config',
        name: 'flow-config',
        component: () => import('@/views/admin/FlowConfig.vue'),
        meta: { title: '流程配置', roles: ['ADMIN'] }
      }
    ]
  },
  // 404 兜底（BlankLayout 外壳）
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/layouts/BlankLayout.vue'),
    meta: { public: true },
    children: [
      {
        path: '',
        name: 'not-found',
        component: () => import('@/views/error/NotFound.vue'),
        meta: { public: true, title: '页面不存在' }
      }
    ]
  }
]

export default routes
