package com.issueflow.service.dict;

import com.issueflow.common.ResultCode;

import java.util.Collection;
import java.util.Map;

/**
 * 字典项引用计数扩展点（ARCH §3.5，F3 平移）。
 * <p>将「字典项删除阻断」从 ISSUE_TYPE / ISSUE_SOURCE 专属补丁，泛化为可注册能力：
 * 任一字典类型只要注册一个实现，其选项被引用时即阻断删除并回填引用计数。</p>
 */
public interface DictItemRefCounter {

    /** 关心的字典类型编码（如 ISSUE_TYPE） */
    String dictCode();

    /**
     * 批量统计引用数：itemCode -> count。
     * <p><b>必须一次 GROUP BY 完成，严禁循环单查（N+1）。</b></p>
     *
     * @param itemCodes 待统计的 item_code 集合
     * @return itemCode -> 引用数
     */
    Map<String, Long> countByItemCodes(Collection<String> itemCodes);

    /** 被引用时抛出的错误码 */
    ResultCode errorCode();

    /**
     * 被引用时的提示文案（含数量占位拼接）。
     *
     * @param count 引用数
     * @return 人类可读提示
     */
    String message(long count);
}
