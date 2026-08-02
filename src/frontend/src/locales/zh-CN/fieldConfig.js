/**
 * 动态字段配置文案（zh-CN）；key 集合必须与 en-US/fieldConfig.js 完全一致。
 * key 规范：fieldConfig.{group}.{semantic}
 */
export default {
  page: {
    title: '字段配置',
    subtitle: '维护问题表单的区域与字段，配置后前台表单即时生效'
  },
  col: {
    name: '名称',
    code: '编码',
    type: '类型',
    refSource: '引用源',
    dependsOn: '依赖字段',
    sort: '排序',
    status: '状态',
    actions: '操作'
  },
  form: {
    section: '所属区域',
    name: '字段名称',
    code: '字段编码',
    i18nKey: '国际化 Key',
    type: '字段类型',
    required: '是否必填',
    placeholder: '占位提示',
    defaultValue: '默认值',
    span: '栅格宽度',
    multiline: '多行文本',
    maxLength: '最大字符数',
    minVal: '最小值',
    maxVal: '最大值',
    decimalScale: '小数位数',
    dictCode: '字典类型',
    refSource: '引用源',
    displayType: '展示形式',
    multiSelect: '允许多选',
    dependsOn: '依赖字段',
    dependsParam: '过滤参数名',
    sort: '排序',
    status: '状态',
    visibleInList: '可作为列表列',
    searchable: '可作为查询条件',
    baseGroup: '基础属性',
    attrGroup: '类型属性',
    advanceGroup: '高级属性'
  },
  drawer: {
    createField: '新增字段',
    editField: '编辑字段',
    preview: '表单预览'
  },
  action: {
    createField: '新增字段',
    preview: '预览表单',
    expandAll: '展开全部',
    collapseAll: '收起全部'
  },
  type: {
    TEXT: '文本',
    NUMBER: '数值',
    DATE: '日期',
    DATETIME: '日期时间',
    DICT: '字典',
    REF: '引用'
  },
  displayType: {
    select: '下拉选择',
    tree: '树形选择'
  },
  nodeType: {
    section: '区域',
    field: '字段'
  },
  tag: {
    system: '内置',
    disabled: '已停用'
  },
  tip: {
    systemFieldDelete: '内置字段不允许删除',
    codeReadonly: '字段编码创建后不可修改',
    typeReadonly: '字段类型创建后不可修改',
    systemAttrLocked: '内置字段仅允许修改名称、必填、提示、栅格、排序与状态',
    sectionReadonly: '区域维护暂未开放（后端未提供区域管理接口），当前仅支持查看',
    dependsPair: '「依赖字段」与「过滤参数名」需同时填写',
    dependsOnly: '仅支持单级依赖：上游字段不能是多选，且自身不能再依赖其他字段',
    selectParentFirst: '请先选择上游字段',
    previewEmpty: '暂无可预览的字段配置',
    noSectionOption: '暂无可选区域，请先在数据库初始化区域数据'
  },
  rules: {
    nameRequired: '请输入字段名称',
    codeRequired: '请输入字段编码',
    codePattern: '编码需以小写字母开头，仅含字母与数字（小驼峰）',
    typeRequired: '请选择字段类型',
    sectionRequired: '请选择所属区域',
    dictCodeRequired: '请选择字典类型',
    refSourceRequired: '请选择引用源',
    dependsParamRequired: '请输入过滤参数名',
    dependsOnRequired: '请选择依赖字段',
    maxLengthExceed: '最多输入 {max} 个字符',
    minValExceed: '不能小于 {min}',
    maxValExceed: '不能大于 {max}'
  },
  msg: {
    createSuccess: '字段新增成功',
    updateSuccess: '字段更新成功',
    deleteSuccess: '字段删除成功',
    deleteConfirm: '确认删除字段「{name}」？删除后已填写的数据将不再展示。',
    toggleSuccess: '状态已更新'
  }
}
