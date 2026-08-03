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
  /**
   * 字段配置弹窗的区块标题与说明。
   * key 由 utils/fieldConfigSchema.js 的 section.titleKey / section.descKey 引用，
   * 形如 fieldConfig.section.base / fieldConfig.section.baseDesc。
   */
  section: {
    base: '基础属性',
    baseDesc: '定义这个字段「是谁」以及「长什么样」：归属区域、名称编码、类型、栅格与默认值。',
    type: '类型属性',
    typeDesc: '随「字段类型」动态显隐，用于约束取值范围与来源，切换类型后不适用的项会自动清空。',
    advanced: '高级属性',
    advancedDesc: '控制字段在列表、查询与整体表单中的表现：排序位置、是否展示为列、是否可检索、是否启用。'
  },
  /**
   * 字段配置弹窗的属性标签。
   * key 由 utils/fieldConfigSchema.js 的 labelKeyOf(code) 引用，形如 fieldConfig.label.name。
   * 与 FIELD_FORM_ITEMS 的 code 一一对应，共 24 项。
   */
  label: {
    sectionId: '所属区域',
    name: '字段名称',
    code: '字段编码',
    i18nKey: '国际化 Key',
    type: '字段类型',
    span: '栅格宽度',
    placeholder: '占位提示',
    defaultValue: '默认值',
    required: '是否必填',
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
    visibleInList: '可作为列表列',
    searchable: '可作为查询条件',
    enabled: '状态'
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
    noSectionOption: '暂无可选区域，请先在数据库初始化区域数据',

    /* ---- 属性问号提示：key 由 fieldConfigSchema.js 的 tipKeyOf(code) 引用，共 24 项 ---- */
    sectionId: '该字段在问题表单中所属的区域分组，决定它渲染在表单的哪一段；区域数据由后台初始化，此处只做选择。',
    name: '展示给填单人看的中文标签，建议 2~8 个字、含义明确，例如「所属产品」；后续可随时修改。',
    code: '字段在接口与数据库中的唯一标识，需小驼峰（小写字母开头，仅含字母数字），如 productLine；创建后不可修改。',
    i18nKey:
      '多语言词条的引用 key，填写后表单标签优先按当前语言取词条，留空则直接展示「字段名称」。',
    type: '决定字段的录入控件与校验方式（文本/数值/日期/字典/引用）；创建后不可修改，请谨慎选择。',
    span: '字段在 24 栅格表单中占据的宽度：24 独占一行，12 为半行，8 为三分之一行；默认 12。',
    placeholder: '输入框为空时显示的浅色引导文案，用于提示填写格式或示例，如「请输入 6 位工单号」。',
    defaultValue: '新建问题时自动带出的初始值，留空表示不预填；需与「字段类型」匹配（数值型请填数字）。',
    required: '开启后该字段为必填项，标签前显示红色星号，未填写时表单提交会被拦截。',
    multiline: '仅文本类型可用；开启后录入控件由单行输入框切换为多行文本域，适合描述类长文本。',
    maxLength: '仅文本类型可用；限制可录入的最大字符数，超出时提示并阻止提交，留空表示不限制。',
    minVal: '仅数值类型可用；允许录入的最小值（含），小于该值时校验不通过，留空表示不设下限。',
    maxVal: '仅数值类型可用；允许录入的最大值（含），大于该值时校验不通过，留空表示不设上限。',
    decimalScale: '仅数值类型可用；允许保留的小数位数，填 0 表示只能录入整数，留空按整数处理。',
    dictCode: '仅字典类型可用；选择该字段的取值来源字典，下拉选项将由所选字典的条目动态生成。',
    refSource: '仅引用类型可用；选择被引用的业务数据源（如产品、用户），选项从该数据源实时拉取。',
    displayType: '仅字典/引用类型可用；决定选择控件的形态——「下拉选择」适合扁平列表，「树形选择」适合层级数据。',
    multiSelect: '仅字典/引用类型可用；开启后可同时选中多个选项，值以数组形式保存。注意：多选字段不能作为其他字段的上游依赖。',
    dependsOn: '设置上游联动字段：本字段的可选项将随上游字段的选中值过滤。仅支持单级联动，需与「过滤参数名」成对填写。',
    dependsParam:
      '向后端查询选项时携带的过滤参数名，其值取自「依赖字段」的当前选中项，如 parentId；需与「依赖字段」成对填写。',
    sort: '同一区域内字段的排列序号，数字越小越靠前；建议留出间隔（如 10、20、30）便于后续插入。',
    visibleInList: '开启后该字段会作为一列出现在问题列表中，便于快速浏览；列过多会影响列表可读性，建议按需开启。',
    searchable: '开启后该字段出现在问题列表的查询条件区，支持按其值检索；仅对区分度高的字段开启为宜。',
    enabled: '字段的启用开关：停用后前台表单不再渲染该字段，但历史已填数据仍保留，可随时重新启用。'
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
