package com.issueflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.PageResult;
import com.issueflow.dto.req.HistoryQueryReq;
import com.issueflow.dto.resp.IssueHistoryVO;
import com.issueflow.entity.IssueHistory;
import com.issueflow.enums.HistoryActionEnum;
import com.issueflow.enums.IssueStatusEnum;
import com.issueflow.mapper.IssueHistoryMapper;
import com.issueflow.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作历史服务：写历史 + 按问题/操作人/时间范围查询
 */
@Service
@RequiredArgsConstructor
public class IssueHistoryService {

    private final IssueHistoryMapper historyMapper;

    /**
     * 记录一条操作历史
     */
    public void record(Long issueId, String action, Integer fromStatus, Integer toStatus,
                       Long operatorId, String remark) {
        IssueHistory history = new IssueHistory();
        history.setIssueId(issueId);
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorId(operatorId);
        history.setRemark(remark);
        historyMapper.insert(history);
    }

    /**
     * 查询某问题的全部历史（时间倒序）
     */
    public java.util.List<IssueHistoryVO> queryByIssue(Long issueId) {
        List<IssueHistoryVO> list = historyMapper.selectByIssue(issueId);
        enrich(list);
        return list;
    }

    /**
     * 分页查询某问题的历史（支持操作人 + 时间范围过滤）
     */
    public PageResult<IssueHistoryVO> queryPageByIssue(Long issueId, HistoryQueryReq req) {
        int pageNum = req.getPage() == null ? 1 : req.getPage();
        int size = req.getSize() == null ? 10 : req.getSize();
        Page<IssueHistoryVO> page = new Page<>(pageNum, size);
        historyMapper.selectPage(page, issueId, req.getOperatorId(),
                DateTimeUtils.parseDate(req.getStart(), true),
                DateTimeUtils.parseDate(req.getEnd(), false));
        enrich(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), (long) pageNum, (long) size);
    }

    /**
     * 补全历史视图的描述字段（动作/状态中文描述）
     */
    private void enrich(List<IssueHistoryVO> list) {
        for (IssueHistoryVO vo : list) {
            HistoryActionEnum action = HistoryActionEnum.getByCode(vo.getAction());
            vo.setActionDesc(action == null ? vo.getAction() : action.getDesc());
            IssueStatusEnum from = IssueStatusEnum.getByCode(vo.getFromStatus());
            vo.setFromStatusDesc(from == null ? "" : from.getDesc());
            IssueStatusEnum to = IssueStatusEnum.getByCode(vo.getToStatus());
            vo.setToStatusDesc(to == null ? "" : to.getDesc());
        }
    }

    /**
     * 逻辑删除某问题的全部历史（删除问题时级联）
     */
    public void deleteByIssue(Long issueId) {
        historyMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IssueHistory>()
                .eq(IssueHistory::getIssueId, issueId));
    }
}
