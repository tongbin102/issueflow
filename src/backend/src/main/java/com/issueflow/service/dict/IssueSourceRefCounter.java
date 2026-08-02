package com.issueflow.service.dict;

import com.issueflow.common.ResultCode;
import com.issueflow.mapper.DictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ISSUE_SOURCE 字典项引用计数（平移自 {@code DictService} 原硬编码分支，ARCH §3.5）。
 * <p>统计「未删除问题中 source 命中该 item_code」的数量；复用
 * {@code DictItemMapper.countIssueBySourceCode} 一次 GROUP BY 出全量，内存过滤本次关心的编码。</p>
 */
@Component
@RequiredArgsConstructor
public class IssueSourceRefCounter implements DictItemRefCounter {

    private final DictItemMapper dictItemMapper;

    @Override
    public String dictCode() {
        return com.issueflow.common.Constants.DICT_TYPE_ISSUE_SOURCE;
    }

    @Override
    public ResultCode errorCode() {
        return ResultCode.DICT_ITEM_HAS_USAGE;
    }

    @Override
    public Map<String, Long> countByItemCodes(Collection<String> itemCodes) {
        Map<String, Long> result = new HashMap<>();
        if (itemCodes == null || itemCodes.isEmpty()) {
            return result;
        }
        Set<String> wanted = new HashSet<>(itemCodes);
        List<Map<String, Object>> rows = dictItemMapper.countIssueBySourceCode();
        for (Map<String, Object> row : rows) {
            Object sc = row.get("sourceCode");
            Object cnt = row.get("cnt");
            if (sc != null && cnt != null && wanted.contains(String.valueOf(sc))) {
                result.put(String.valueOf(sc), Long.valueOf(cnt.toString()));
            }
        }
        return result;
    }

    @Override
    public String message(long count) {
        return "该选项下存在 " + count + " 个问题，无法删除，可改为停用";
    }
}
