package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.FlowTransition;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程流转规则 Mapper。
 * <p>flow_transition(from_node_id,to_node_id) 建有唯一索引（含逻辑删除行），
 * 删除/重置必须物理 DELETE，否则同向流转无法重建。</p>
 */
@Mapper
public interface FlowTransitionMapper extends BaseMapper<FlowTransition> {

    /** 物理删除单条流转（绕过 @TableLogic 逻辑删除） */
    @Delete("DELETE FROM flow_transition WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /** 物理清空全部流转（恢复默认流程用） */
    @Delete("DELETE FROM flow_transition")
    int physicalDeleteAll();
}
