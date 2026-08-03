/**
 * en-US message aggregation (mirrors zh-CN structure exactly)
 */
import common from './common'
import enumDict from './enum'
import menu from './menu'
import menuManage from './menuManage'
import layout from './layout'
import locale from './locale'
import theme from './theme'
import login from './login'
import error from './error'
import issue from './issue'
import dashboard from './dashboard'
import project from './project'
import moduleDict from './module'
import org from './org'
import user from './user'
import role from './role'
import flow from './flow'
import system from './system'
import site from './site'
import chart from './chart'
import dict from './dict'
import profile from './profile'
import infra from './infra'
import fieldConfig from './fieldConfig'
// Phase10 requirement 3: data management copy (backup / restore / upload / retention / reset)
import dataManagement from './dataManagement'

export default {
  common,
  enum: enumDict,
  menu: { ...menu, ...menuManage },
  layout,
  locale,
  theme,
  login,
  error,
  issue,
  dashboard,
  project,
  module: moduleDict,
  org,
  user,
  role,
  flow,
  system,
  site,
  chart,
  dict,
  profile,
  infra,
  fieldConfig,
  dataManagement
}
