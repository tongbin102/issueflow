package com.issueflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.entity.Role;
import com.issueflow.entity.User;
import com.issueflow.entity.UserRole;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.UserMapper;
import com.issueflow.mapper.UserRoleMapper;
import com.issueflow.common.Constants;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * issueFlow 后端启动类
 * - 扫描 Mapper：com.issueflow.mapper
 * - 启动时若 admin 账号不存在则用 BCrypt 写入默认管理员
 */
@SpringBootApplication
@org.mybatis.spring.annotation.MapperScan("com.issueflow.mapper")
public class IssueFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(IssueFlowApplication.class, args);
    }

    /**
     * 初始化默认管理员账号
     * 默认：username=admin, password=admin123, role=ADMIN
     */
    @Bean
    public ApplicationRunner initAdminUser(UserMapper userMapper,
                                           RoleMapper roleMapper,
                                           UserRoleMapper userRoleMapper,
                                           PasswordEncoder passwordEncoder) {
        return args -> {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
            if (count != null && count > 0) {
                return;
            }
            Role adminRole = roleMapper.selectOne(
                    new LambdaQueryWrapper<Role>().eq(Role::getCode, Constants.ROLE_ADMIN));
            if (adminRole == null) {
                throw new IllegalStateException("ADMIN 角色未初始化，请确认 data.sql 已执行");
            }
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRealName("系统管理员");
            admin.setRoleId(adminRole.getId());
            // Phase8 W3 #11：多角色 —— 同步写 roles 冗余列与 user_role 关系
            admin.setRoles(List.of(Constants.ROLE_ADMIN));
            admin.setStatus(1);
            userMapper.insert(admin);
            UserRole adminUserRole = new UserRole();
            adminUserRole.setUserId(admin.getId());
            adminUserRole.setRoleCode(Constants.ROLE_ADMIN);
            userRoleMapper.insert(adminUserRole);
        };
    }
}
