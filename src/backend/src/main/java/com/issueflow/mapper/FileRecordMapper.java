package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.FileRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件记录 Mapper
 */
@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {

    /**
     * 统计未删除文件的总占用字节数。
     *
     * @return 总字节数，无数据返回 0
     */
    @Select("SELECT COALESCE(SUM(file_size), 0) FROM file_record WHERE deleted = 0")
    Long sumSize();

    /**
     * 查询「已软删且早于指定时间」的记录，供内置清理任务物理回收。
     *
     * <p>必须走原生 SQL：MyBatis-Plus 逻辑删除插件会给 Wrapper 查询自动附加
     * {@code deleted = 0}，用 Wrapper 永远查不到软删行。</p>
     *
     * @param before 截止时间（比较 updated_at）
     * @param limit  单次最多返回条数
     * @return 待清理的记录列表
     */
    @Select("SELECT * FROM file_record WHERE deleted = 1 AND updated_at IS NOT NULL "
            + "AND updated_at < #{before} ORDER BY id ASC LIMIT #{limit}")
    List<FileRecord> selectSoftDeletedBefore(@Param("before") LocalDateTime before,
                                             @Param("limit") int limit);

    /**
     * 物理删除一行（仅供清理任务在物理文件已删除后调用）。
     *
     * @param id 记录 id
     * @return 影响行数
     */
    @Delete("DELETE FROM file_record WHERE id = #{id} AND deleted = 1")
    int hardDeleteById(@Param("id") Long id);
}
