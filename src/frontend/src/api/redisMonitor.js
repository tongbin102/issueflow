import request from './request'

/**
 * Redis 监控 API（Phase7 T7，对应后端 RedisMonitorController）。
 *
 * <p><b>只读</b>：本模块只有一个 GET 接口，禁止新增任何写命令封装。</p>
 *
 * <p><b>降级契约</b>：Redis 不可用时后端仍返回 HTTP 200 + code 200，
 * 载荷为 {@code {available:false, errorMessage}}，因此本函数<b>不会 reject</b>，
 * 调用方必须判断 available 字段而不是靠 catch。</p>
 */

/**
 * 获取 Redis 运行信息（redis:monitor）。
 * @returns {Promise<{available:boolean,errorMessage:string,server:Object,memory:Object,stats:Object,keyspace:Array,dbSize:number}>}
 */
export function getRedisInfo() {
  // 单独收紧超时：后端已限制 2s，前端 8s 兜底，避免监控页长时间转圈
  return request.get('/admin/redis/info', { timeout: 8000 })
}
