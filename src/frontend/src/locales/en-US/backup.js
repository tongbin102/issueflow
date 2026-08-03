/**
 * @deprecated Deprecated as of Phase10 requirement 3; no remaining references.
 *
 * The former "Data Maintenance / Backup" copy (Phase7 T8) was retired together
 * with BackupDrawer.vue. All backup / restore / upload / retention copy is now
 * owned by `dataManagement.js`.
 *
 * An empty object is kept so the existing `backup` namespace reference in
 * `index.js` keeps resolving, while guaranteeing no legacy "Backup Settings"
 * business copy remains in the frontend.
 * Paired with zh-CN/backup.js — both empty, so key sets match by construction.
 */
export default {}
