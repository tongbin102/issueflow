package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.UserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色映射 Mapper（Phase8 W3 #11 新增）
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 查询某用户已分配的角色码集合（按插入顺序，首个视为主角色）
     *
     * @param userId 用户 id
     * @return 角色码列表，无分配时为空列表
     */
    @Select("SELECT role_code FROM user_role WHERE user_id = #{userId} ORDER BY id")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 物理删除某用户的全部角色映射（关联随用户重建，无需逻辑删除）
     *
     * @param userId 用户 id
     * @return 影响行数
     */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 批量插入用户角色映射
     *
     * @param list 映射列表（调用方保证非空且已去重）
     * @return 影响行数
     */
    @Insert("<script>"
            + "INSERT INTO user_role (user_id, role_code) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.userId}, #{item.roleCode})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<UserRole> list);
}
