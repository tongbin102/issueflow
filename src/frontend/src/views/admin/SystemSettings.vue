<script>
/**
 * @deprecated Phase10 需求三起废弃，已无任何路由指向本组件。
 *
 * 原「备份设置」页（/admin/system/settings）的唯一功能「数据初始化」，
 * 已整体迁移到数据管理页 `/admin/system/data-management`
 * （src/views/admin/data-management/index.vue）的「危险操作」分区，
 * 那里同时提供备份、下载、恢复、上传恢复与保留策略。
 *
 * 路由 /admin/system/settings 现已重定向到数据管理页（见 router/routes.js），
 * 本文件仅作为占位保留，避免历史分支合并时出现「文件不存在」冲突；
 * 内部不再包含任何模板、业务文案与接口调用。
 * 后续清理批次可直接移除本文件，无需评估影响面。
 */
export default {
  name: 'SystemSettingsDeprecated'
}
</script>
