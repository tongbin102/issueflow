package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.Dict;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper（对应表 dict）
 */
@Mapper
public interface DictMapper extends BaseMapper<Dict> {
}
