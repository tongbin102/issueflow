package com.issueflow.service.dict;

import com.issueflow.common.ResultCode;
import com.issueflow.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ISSUE_TYPE 字典项引用计数（平移自原 {@code IssueTypeService} 的删除阻断，ARCH §3.5）。
 * <p>统计「未删除问题中 type_code 命中该 item_code」的数量；复用 {@code IssueMapper.countGroupByTypeCode}
 * 一次 GROUP BY 出全量，内存过滤出本次关心的编码，杜绝 N+1。</p>
 */
@Component
@RequiredArgsConstructor
public class IssueTypeRefCounter implements DictItemRefCounter {

    private final IssueMapper issueMapper;

    @Override
    public String dictCode() {
        return "ISSUE_TYPE";
    }

    @Override
    public ResultCode errorCode() {
        return ResultCode.ISSUE_TYPE_HAS_USAGE;
    }

    @Override
    public Map<String, Long> countByItemCodes(Collection<String> itemCodes) {
        Map<String, Long> result = new HashMap<>();
        if (itemCodes == null || itemCodes.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = issueMapper.countGroupByTypeCode(itemCodes);
        for (Map<String, Object> row : rows) {
            Object tc = row.get("typeCode");
            Object cnt = row.get("cnt");
            if (tc != null && cnt != null) {
                result.put(String.valueOf(tc), Long.valueOf(cnt.toString()));
            }
        }
        return result;
    }

    @Override
    public String message(long count) {
        return "该类型下存在 " + count + " 个问题，无法删除，可改为停用";
    }
}
