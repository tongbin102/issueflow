package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.DictItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 字典项 Mapper（对应表 dict_item）
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItem> {

    /**
     * 一条 SQL 聚合出「每个来源（item_code）被多少条问题引用」，用于列表 refCount 批量回填。
     * <p>严禁每行一次 COUNT（N+1）。issue.source 为 varchar，直接存 dict_item 的 item_code。</p>
     *
     * @return 每行含 sourceCode / cnt 两个键
     */
    @Select("SELECT source AS sourceCode, COUNT(*) AS cnt FROM issue "
            + "WHERE deleted = 0 AND source IS NOT NULL AND source <> '' GROUP BY source")
    List<Map<String, Object>> countIssueBySourceCode();
}
