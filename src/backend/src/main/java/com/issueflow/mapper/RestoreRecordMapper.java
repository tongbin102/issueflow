package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.RestoreRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 恢复记录 Mapper（Phase10 数据管理）。
 */
@Mapper
public interface RestoreRecordMapper extends BaseMapper<RestoreRecord> {
}
