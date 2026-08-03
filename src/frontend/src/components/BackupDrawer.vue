<script>
/**
 * @deprecated Phase10 需求三起废弃，已无任何引用方。
 *
 * 原「数据维护 / 备份数据」抽屉（Phase7 T8）承担的导出能力，
 * 已整体迁移到数据管理页 `/admin/system/data-management`
 * （src/views/admin/data-management/index.vue），
 * 那里提供备份、下载、恢复、上传恢复与保留策略的完整闭环。
 *
 * 本文件仅作为占位保留，避免历史分支合并时出现「文件不存在」冲突；
 * 内部不再包含任何模板、业务文案与接口调用。
 * 后续清理批次可直接移除本文件，无需评估影响面。
 */
export default {
  name: 'BackupDrawerDeprecated'
}
</script>
