package com.issueflow.util;

import com.issueflow.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 问题编号生成器：IS-YYYYMMDD-序号（每日 0001 起，位数补零到 4 位）
 * <p>并发兜底由 issue 表 issue_no 唯一索引保证（插入冲突由 Service 捕获重试一次）。</p>
 */
@Component
@RequiredArgsConstructor
public class IssueNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String PREFIX = "IS-";

    private static final int SEQ_WIDTH = 4;

    private final IssueMapper issueMapper;

    /**
     * 生成下一个问题编号
     * <p>取当日日期，统计已存在的 IS-YYYYMMDD-% 数量 + 1，格式化如 IS-20250601-0001。</p>
     *
     * @return 问题编号
     */
    public String nextIssueNo() {
        String date = LocalDateTime.now().format(DATE_FMT);
        String likePrefix = PREFIX + date + "-";
        Long count = issueMapper.countByIssueNoPrefix(likePrefix);
        long next = (count == null ? 0L : count) + 1;
        return likePrefix + String.format("%0" + SEQ_WIDTH + "d", next);
    }
}
