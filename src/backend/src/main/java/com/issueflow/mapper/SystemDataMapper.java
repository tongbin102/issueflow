package com.issueflow.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 数据初始化 Mapper（R7）：物理 DELETE（先子后父）+ AUTO_INCREMENT 重置。
 * <p>保留表：role / permission / role_permission / menu / sys_config /
 * flow_node / flow_transition + admin 账号。</p>
 */
@Mapper
public interface SystemDataMapper {

    /** 收集全部附件磁盘路径（清库前调用，供事务提交后删盘） */
    @Select("SELECT file_path FROM issue_attachment")
    List<String> selectAllAttachmentPaths();

    @Delete("DELETE FROM issue_attachment")
    int clearIssueAttachment();

    @Delete("DELETE FROM issue_history")
    int clearIssueHistory();

    @Delete("DELETE FROM issue_relation")
    int clearIssueRelation();

    @Delete("DELETE FROM issue")
    int clearIssue();

    @Delete("DELETE FROM tag")
    int clearTag();

    @Delete("DELETE FROM module_dependency")
    int clearModuleDependency();

    @Delete("DELETE FROM `module`")
    int clearModule();

    @Delete("DELETE FROM project")
    int clearProject();

    @Delete("DELETE FROM organization")
    int clearOrganization();

    /** 删除除 admin 外的全部用户（物理删除） */
    @Delete("DELETE FROM `user` WHERE username <> 'admin'")
    int clearUsersExceptAdmin();

    /** admin 的上级领导引用置空（被删用户可能被引用） */
    @Update("UPDATE `user` SET leader_id = NULL WHERE username = 'admin'")
    int resetAdminLeader();

    /** 重置表自增值（表名来自服务层白名单，非用户输入，${} 安全） */
    @Update("ALTER TABLE `${tableName}` AUTO_INCREMENT = 1")
    int resetAutoIncrement(@Param("tableName") String tableName);
}
