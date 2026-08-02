import { defineStore } from 'pinia'
import { getDictOptions } from '@/api/dict'

/**
 * 字典下拉缓存 store（按 typeCode 分片）。
 *
 * options[typeCode]    = 仅启用项（新建/编辑表单数据源）
 * allOptions[typeCode] = 全量含停用项（筛选下拉数据源，「(已停用)」后缀由组件按 i18n 拼接）
 *
 * 后台字典写操作成功后调用 invalidate(typeCode) 失效对应分片，
 * 不刷新页面即可让全站下拉在下次访问时拿到最新数据。
 */
export const useDictStore = defineStore('dict', {
  state: () => ({
    /** @type {Record<string, Array>} 启用项分片 */
    options: {},
    /** @type {Record<string, Array>} 全量分片 */
    allOptions: {},
    /** @type {Record<string, boolean>} 启用项分片已加载标记 */
    loaded: {},
    /** @type {Record<string, boolean>} 全量分片已加载标记 */
    allLoaded: {},
    loading: false
  }),
  actions: {
    /**
     * 拉取某类型的启用项下拉（带缓存）。
     * @param {string} typeCode 字典类型编码
     * @param {boolean} force 强制刷新
     * @returns {Promise<Array>}
     */
    async fetchOptions(typeCode, force = false) {
      if (!typeCode) return []
      if (this.loaded[typeCode] && !force) return this.options[typeCode] || []
      this.loading = true
      try {
        const data = await getDictOptions(typeCode, false)
        this.options[typeCode] = data || []
        this.loaded[typeCode] = true
        return this.options[typeCode]
      } catch (e) {
        this.options[typeCode] = this.options[typeCode] || []
        return this.options[typeCode]
      } finally {
        this.loading = false
      }
    },
    /**
     * 拉取某类型的全量下拉（含停用项，筛选场景）。
     * @param {string} typeCode 字典类型编码
     * @param {boolean} force 强制刷新
     * @returns {Promise<Array>}
     */
    async fetchAllOptions(typeCode, force = false) {
      if (!typeCode) return []
      if (this.allLoaded[typeCode] && !force) return this.allOptions[typeCode] || []
      this.loading = true
      try {
        const data = await getDictOptions(typeCode, true)
        this.allOptions[typeCode] = data || []
        this.allLoaded[typeCode] = true
        return this.allOptions[typeCode]
      } catch (e) {
        this.allOptions[typeCode] = this.allOptions[typeCode] || []
        return this.allOptions[typeCode]
      } finally {
        this.loading = false
      }
    },
    /**
     * 同步读取启用项分片（不触发请求，供 computed 渲染）。
     * @param {string} typeCode
     * @returns {Array}
     */
    optionsOf(typeCode) {
      return this.options[typeCode] || []
    },
    /**
     * 同步读取全量分片（不触发请求）。
     * @param {string} typeCode
     * @returns {Array}
     */
    allOptionsOf(typeCode) {
      return this.allOptions[typeCode] || []
    },
    /**
     * 按选项编码取名称（优先查全量分片；业务取值一律用 code，与 issue.source 落库口径一致）。
     * @param {string} typeCode 字典类型编码
     * @param {string} code 选项编码（item_code）
     * @returns {string} 未命中返回空串
     */
    nameOf(typeCode, code) {
      if (code == null || code === '') return ''
      const all = this.allOptions[typeCode] || []
      const enabledOnly = this.options[typeCode] || []
      const hit = all.find((o) => o.code === code) || enabledOnly.find((o) => o.code === code)
      return hit ? hit.name : ''
    },
    /**
     * 按 item_code 取选项对象（优先查全量分片）。
     *
     * <p>Phase7：`issue.source` 落库的是 item_code，故列表/表单回显需按 code 反查。</p>
     *
     * @param {string} typeCode 字典类型编码
     * @param {string} code 字典项编码
     * @returns {{id:number,name:string,code:string,enabled:boolean}|null}
     */
    optionByCode(typeCode, code) {
      if (!code) return null
      const all = this.allOptions[typeCode] || []
      const enabledOnly = this.options[typeCode] || []
      return all.find((o) => o.code === code) || enabledOnly.find((o) => o.code === code) || null
    },
    /**
     * 按 item_code 取选项名（未命中返回空串）。
     * @param {string} typeCode 字典类型编码
     * @param {string} code 字典项编码
     * @returns {string}
     */
    nameOfCode(typeCode, code) {
      const hit = this.optionByCode(typeCode, code)
      return hit ? hit.name : ''
    },
    /**
     * 失效缓存：传 typeCode 只失效该分片，不传则全量失效。
     * @param {string} [typeCode]
     */
    invalidate(typeCode) {
      if (typeCode) {
        delete this.loaded[typeCode]
        delete this.allLoaded[typeCode]
        return
      }
      this.loaded = {}
      this.allLoaded = {}
    }
  }
})
