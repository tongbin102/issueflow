package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.RolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限映射 Mapper
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 查询某角色已分配的权限码集合（JOIN permission 取 code）
     */
    @Select("SELECT p.code FROM role_permission rp "
            + "JOIN permission p ON p.id = rp.permission_id "
            + "WHERE rp.role_id = #{roleId} AND p.deleted = 0")
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);

    /**
     * 物理删除某角色的全部权限映射（关联随角色重建，无需逻辑删除）
     */
    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId}")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色权限映射
     */
    @Insert("<script>"
            + "INSERT INTO role_permission (role_id, permission_id, created_at) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.roleId}, #{item.permissionId}, #{item.createdAt})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<RolePermission> list);
}
