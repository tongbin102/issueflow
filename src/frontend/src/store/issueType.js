import { defineStore } from 'pinia'
import { listIssueTypeOptions } from '@/api/issueType'

/**
 * 问题类型下拉缓存 store。
 * options    = 仅启用项（新建/编辑表单数据源）
 * allOptions = 全量含停用项（筛选下拉数据源，「(已停用)」后缀由组件按 i18n 拼接）
 * 单页面生命周期内不重复请求；后台写操作成功后调用 invalidate() 失效缓存。
 */
export const useIssueTypeStore = defineStore('issueType', {
  state: () => ({
    options: [],
    allOptions: [],
    loaded: false,
    allLoaded: false,
    loading: false
  }),
  actions: {
    /**
     * 拉取启用项下拉（带缓存）。
     * @param {boolean} force 强制刷新
     * @returns {Promise<Array>}
     */
    async fetchOptions(force = false) {
      if (this.loaded && !force) return this.options
      const data = await listIssueTypeOptions(false)
      this.options = data || []
      this.loaded = true
      return this.options
    },
    /**
     * 拉取全量下拉（含停用项，筛选场景）。
     * @param {boolean} force 强制刷新
     * @returns {Promise<Array>}
     */
    async fetchAllOptions(force = false) {
      if (this.allLoaded && !force) return this.allOptions
      const data = await listIssueTypeOptions(true)
      this.allOptions = data || []
      this.allLoaded = true
      return this.allOptions
    },
    /**
     * 按 id 取类型名（优先查全量缓存）。
     * @param {number} id
     * @returns {string}
     */
    nameOf(id) {
      if (id == null) return ''
      const hit =
        this.allOptions.find((o) => o.id === id) || this.options.find((o) => o.id === id)
      return hit ? hit.name : ''
    },
    /** 写操作后失效缓存（下次访问重新拉取） */
    invalidate() {
      this.loaded = false
      this.allLoaded = false
    }
  }
})
