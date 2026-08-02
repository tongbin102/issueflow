import { defineStore } from 'pinia'
import { getFieldSchema } from '@/api/fieldConfig'

/**
 * 字段渲染契约（FieldSchemaVO）缓存 store。
 *
 * <p>契约来源：{@code FieldConfigController#schema}，登录即可访问（无权限码）。
 * 一次会话内只拉取一次，供 IssueForm / IssueDetailDrawer 等多个消费点共享，
 * 避免每次打开抽屉都打一次 {@code /field-configs/schema}。</p>
 *
 * <p>version 为全量 field_config 的最大 updated_at 毫秒，可作为本地缓存比对键；
 * 本期仅在抽屉打开时 ensure() 一次，未做「轮询比对自动刷新」。</p>
 */
export const useFieldSchemaStore = defineStore('fieldSchema', {
  state: () => ({
    /** FieldSchemaVO | null */
    schema: null,
    /** 全量字段配置最大更新时间（Long, ms） */
    version: null,
    /** 是否已成功加载过一次 */
    loaded: false
  }),
  getters: {
    /** 业务区域列表（可能为空） */
    sections: (state) => (state.schema && Array.isArray(state.schema.sections) ? state.schema.sections : []),
    /** 固定系统页签（后端下发，恒为 ['attachment','relation','history']；缺省兜底） */
    systemTabs: (state) => {
      const st = state.schema && state.schema.systemTabs
      return Array.isArray(st) && st.length ? st : ['attachment', 'relation', 'history']
    },
    /** 全部自定义字段（system=false）的 FieldConfigVO，按 schema 顺序 */
    customFields: (state) => {
      const secs = state.schema && state.schema.sections
      const list = []
      if (!Array.isArray(secs)) return list
      secs.forEach((section) => {
        ;(section.fields || []).forEach((f) => {
          if (f && f.system === false) list.push(f)
        })
      })
      return list
    },
    /** 自定义字段编码集合（用于提交时拆分 customFields） */
    customCodes: (state) => {
      const list = []
      const secs = state.schema && state.schema.sections
      if (!Array.isArray(secs)) return list
      secs.forEach((section) => {
        ;(section.fields || []).forEach((f) => {
          if (f && f.system === false && f.code) list.push(f.code)
        })
      })
      return list
    }
  },
  actions: {
    /**
     * 拉取 schema（带进程内并发去重与缓存）。
     * 已 loaded 且非 force 时直接返回缓存；正在请求中则复用同一 Promise。
     *
     * @param {boolean} [force] 强制刷新（忽略本地缓存）
     * @returns {Promise<object|null>} FieldSchemaVO
     */
    async loadSchema(force = false) {
      if (this.loaded && !force && this.schema) return this.schema
      if (inflight) return inflight
      inflight = getFieldSchema()
        .then((data) => {
          this.schema = data || null
          this.version = data && data.version != null ? data.version : null
          this.loaded = true
          return this.schema
        })
        .catch((e) => {
          this.schema = null
          this.loaded = false
          throw e
        })
        .finally(() => {
          inflight = null
        })
      return inflight
    },
    /** 等价别名：供消费点在每次打开抽屉时调用，确保 schema 就绪 */
    async ensure(force = false) {
      return this.loadSchema(force)
    }
  }
})

/** 进程内并发请求锁，避免同一时刻重复打 /field-configs/schema */
let inflight = null
