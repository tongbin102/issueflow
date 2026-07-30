package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.Module;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模块 Mapper（无自定义 SQL，全部走 LambdaQueryWrapper）
 */
@Mapper
public interface ModuleMapper extends BaseMapper<Module> {
}
