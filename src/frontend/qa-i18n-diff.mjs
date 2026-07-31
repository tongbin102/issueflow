const flat = (o, p = '') => Object.entries(o).flatMap(([k, v]) =>
  v && typeof v === 'object' && !Array.isArray(v) ? flat(v, p + k + '.') : [p + k]);
const zh = (await import('./src/locales/zh-CN/user.js')).default;
const en = (await import('./src/locales/en-US/user.js')).default;
const zk = flat(zh), ek = flat(en);
const zs = new Set(zk), es = new Set(ek);
console.log('zh-CN keys:', zk.length, '| en-US keys:', ek.length);
const missEn = zk.filter(k => !es.has(k));
const missZh = ek.filter(k => !zs.has(k));
console.log('\n[en-US 缺失]', missEn.length ? missEn : '(none)');
console.log('[zh-CN 缺失]', missZh.length ? missZh : '(none)');
const w3 = ['form.roles', 'placeholder.selectRoles', 'tip.primaryRole', 'msg.rolesRequired'];
console.log('\n--- W3 关键键位 ---');
for (const k of w3) console.log(k.padEnd(28), 'zh:', zs.has(k) ? 'OK' : 'MISSING', '| en:', es.has(k) ? 'OK' : 'MISSING');
const val = (o, k) => k.split('.').reduce((c, s) => c[s], o);
console.log('\n[zh 空值]', zk.filter(k => !String(val(zh, k)).trim()).length || '(none)');
console.log('[en 空值]', ek.filter(k => !String(val(en, k)).trim()).length || '(none)');
console.log('\n--- W3 键实际取值 ---');
for (const k of w3) {
  if (zs.has(k)) console.log(k.padEnd(28), 'zh=', JSON.stringify(val(zh, k)), '| en=', es.has(k) ? JSON.stringify(val(en, k)) : 'MISSING');
}
