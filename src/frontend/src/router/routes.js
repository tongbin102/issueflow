// 路由表：按布局分组，页面级权限通过 meta.roles 控制。
// T9/T10：在 user/ 与 admin/ 布局下挂载全部业务路由（原占位 Index.vue 已不再引用）。
// Phase6（T3）：
//   1. meta.title 一律存 i18n key（布局用 te() 命中翻译 / 未命中回退原值）；
//   2. /user/submit-issue 改为 redirect 到 /user/my-issues（提交入口收敛为列表页抽屉，兼容旧书签）；
//   3. 新增 /admin/issue-types（Q5：与「问题管理」同级平铺的兄弟菜单）；
//   4. 新增 /admin/system/site（网站设置）。
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
        meta: { public: true, title: 'login.title' }
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
        meta: { public: true, title: 'error.403.title' }
      }
    ]
  },
  // 用户界面（UserLayout）：提交者 / 开发 / 测试 / 管理员 可见
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    redirect: '/user',
    children: [
      {
        path: 'user',
        name: 'user-dashboard',
        component: () => import('@/views/user/UserDashboard.vue'),
        meta: { title: 'menu.user.dashboard', roles: USER_ROLES }
      },
      {
        path: 'user/my-issues',
        name: 'user-issues',
        component: () => import('@/views/user/UserIssueList.vue'),
        meta: { title: 'menu.user.myIssues', roles: USER_ROLES }
      },
      {
        // Phase6：提交问题页下线，统一走「我的问题」列表页的新建抽屉；保留路径兼容旧书签
        path: 'user/submit-issue',
        redirect: '/user/my-issues'
      },
      {
        path: 'user/stats',
        name: 'user-stats',
        component: () => import('@/views/user/UserStats.vue'),
        meta: { title: 'menu.user.stats', roles: USER_ROLES }
      }
    ]
  },
  // 管理后台（AdminLayout）：仅 ADMIN
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/index',
    meta: { roles: ['ADMIN'] },
    children: [
      {
        path: 'index',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: 'menu.admin.overview', roles: ['ADMIN'] }
      },
      {
        path: 'issues',
        name: 'admin-issues',
        component: () => import('@/views/admin/AdminIssueList.vue'),
        meta: { title: 'menu.admin.issues', roles: ['ADMIN'] }
      },
      {
        // Phase6 新增（Q5 决策：与问题管理同级平铺，不做父子嵌套）
        path: 'issue-types',
        name: 'issue-type-manage',
        component: () => import('@/views/admin/IssueTypeManage.vue'),
        meta: { title: 'menu.admin.issueTypes', roles: ['ADMIN'] }
      },
      {
        path: 'projects',
        name: 'project-manage',
        component: () => import('@/views/admin/ProjectManage.vue'),
        meta: { title: 'menu.admin.projects', roles: ['ADMIN'] }
      },
      {
        path: 'modules',
        name: 'module-manage',
        component: () => import('@/views/admin/ModuleManage.vue'),
        meta: { title: 'menu.admin.modules', roles: ['ADMIN'] }
      },
      {
        path: 'flow-monitor',
        name: 'flow-monitor',
        component: () => import('@/views/admin/FlowMonitor.vue'),
        meta: { title: 'menu.admin.flowMonitor', roles: ['ADMIN'] }
      },
      {
        path: 'system',
        component: () => import('@/views/admin/SystemLayout.vue'),
        redirect: '/admin/system/organizations',
        meta: { title: 'menu.admin.system', roles: ['ADMIN'] },
        children: [
          {
            path: 'organizations',
            name: 'organization-manage',
            component: () => import('@/views/admin/OrganizationManage.vue'),
            meta: { title: 'menu.admin.organizations', roles: ['ADMIN'] }
          },
          {
            path: 'menus',
            name: 'menu-manage',
            component: () => import('@/views/admin/MenuManage.vue'),
            meta: { title: 'menu.admin.menus', roles: ['ADMIN'] }
          },
          {
            path: 'users',
            name: 'user-manage',
            component: () => import('@/views/admin/UserManage.vue'),
            meta: { title: 'menu.admin.users', roles: ['ADMIN'] }
          },
          {
            path: 'roles',
            name: 'role-manage',
            component: () => import('@/views/admin/RoleManage.vue'),
            meta: { title: 'menu.admin.roles', roles: ['ADMIN'] }
          },
          {
            // Phase6 新增：网站设置（名称/简称/副标题/默认主题/默认语言/版权/备案号）
            path: 'site',
            name: 'site-settings',
            component: () => import('@/views/admin/SiteSettings.vue'),
            meta: { title: 'menu.admin.siteSettings', roles: ['ADMIN'] }
          },
          {
            path: 'settings',
            name: 'system-settings',
            component: () => import('@/views/admin/SystemSettings.vue'),
            meta: { title: 'menu.admin.systemSettings', roles: ['ADMIN'] }
          }
        ]
      },
      {
        path: 'flow-config',
        name: 'flow-config',
        component: () => import('@/views/admin/FlowConfig.vue'),
        meta: { title: 'menu.admin.flowConfig', roles: ['ADMIN'] }
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
        meta: { public: true, title: 'error.404.title' }
      }
    ]
  }
]

export default routes
