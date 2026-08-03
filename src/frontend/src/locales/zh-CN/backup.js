/**
 * @deprecated Phase10 需求三起废弃，已无任何引用方。
 *
 * 原「数据维护 / 备份数据」文案（Phase7 T8）随 BackupDrawer.vue 一并下线，
 * 备份 / 恢复 / 上传 / 保留策略的全部文案统一由 `dataManagement.js` 承接。
 *
 * 保留空对象是为了让 `index.js` 中既有的 `backup` 命名空间引用不至于报错，
 * 同时确保前端不再残留任何「备份设置」时代的业务文案。
 * 与 en-US/backup.js 同为空对象，key 集合天然成对。
 */
export default {}
