package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.FlowNode;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程节点 Mapper。
 * <p>flow_node.status_code 建有唯一索引（含逻辑删除行），删除/重置必须物理 DELETE，
 * 否则同状态码节点无法重建。</p>
 */
@Mapper
public interface FlowNodeMapper extends BaseMapper<FlowNode> {

    /** 物理删除单个节点（绕过 @TableLogic 逻辑删除） */
    @Delete("DELETE FROM flow_node WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /** 物理清空全部节点（恢复默认流程用） */
    @Delete("DELETE FROM flow_node")
    int physicalDeleteAll();
}
