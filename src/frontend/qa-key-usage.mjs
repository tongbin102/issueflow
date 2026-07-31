import fs from 'fs';
const flat = (o, p = '') => Object.entries(o).flatMap(([k, v]) =>
  v && typeof v === 'object' && !Array.isArray(v) ? flat(v, p + k + '.') : [p + k]);
const zh = (await import('./src/locales/zh-CN/user.js')).default;
const en = (await import('./src/locales/en-US/user.js')).default;
const zs = new Set(flat(zh)), es = new Set(flat(en));
const src = fs.readFileSync('./src/views/admin/UserManage.vue', 'utf8');
const used = [...new Set([...src.matchAll(/t\(\s*'user\.([a-zA-Z0-9_.]+)'/g)].map(m => m[1]))];
console.log('UserManage.vue 使用 user.* 键数:', used.length);
const undef = used.filter(k => !zs.has(k));
const undefEn = used.filter(k => !es.has(k));
console.log('[zh 未定义]', undef.length ? undef : '(none)');
console.log('[en 未定义]', undefEn.length ? undefEn : '(none)');
const unused = [...zs].filter(k => !used.includes(k));
console.log('[locale 中定义但本组件未用]', unused.length, unused.length ? unused : '');
