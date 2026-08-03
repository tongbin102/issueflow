package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.BackupRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份记录 Mapper（Phase10 数据管理）。
 */
@Mapper
public interface BackupRecordMapper extends BaseMapper<BackupRecord> {
}
