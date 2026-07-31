package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.FileConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件存储配置 Mapper（对应表 file_config，全局唯一一行）
 */
@Mapper
public interface FileConfigMapper extends BaseMapper<FileConfig> {
}
