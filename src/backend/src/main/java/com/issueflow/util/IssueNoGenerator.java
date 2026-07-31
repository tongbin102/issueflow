package com.issueflow.util;

import com.issueflow.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 问题编号生成器：IS-YYYYMMDD-序号（每日 0001 起，位数补零到 4 位）
 * <p>并发与软删兜底：基于当日「最大已用序号 + 1」生成（含逻辑删除行），由 issue 表 issue_no
 * 唯一索引兜底；发生插入冲突时由 Service 层循环重试（每次重新生成编号）。</p>
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
     * <p>取当日日期，查询当日已用最大序号（含逻辑删除行，因唯一索引 uk_issue_no 覆盖软删数据）
     * + 1，格式化如 IS-20250601-0001。基于最大序号而非计数，避免软删导致序号回退。</p>
     *
     * @return 问题编号
     */
    public String nextIssueNo() {
        String date = LocalDateTime.now().format(DATE_FMT);
        String likePrefix = PREFIX + date + "-";
        Long maxSeq = issueMapper.maxSeqByIssueNoPrefix(likePrefix);
        long next = (maxSeq == null ? 0L : maxSeq) + 1;
        return likePrefix + String.format("%0" + SEQ_WIDTH + "d", next);
    }
}
