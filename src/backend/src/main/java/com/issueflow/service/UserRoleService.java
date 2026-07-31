package com.issueflow.service;

import com.issueflow.entity.Role;
import com.issueflow.entity.UserRole;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户-角色关系服务（Phase8 W3 #11 新增）。
 *
 * <p>职责边界：只负责 {@code user_role} 关系表的读写与角色码合法性校验，
 * 不感知 {@code user.role_id}（主角色）与 {@code user.roles}（JSON 冗余列）——
 * 那两者由 {@link UserService} 统一维护，避免两处写同一份数据。</p>
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /**
     * 查询用户已分配的角色码集合。
     *
     * @param userId 用户 id（为 null 返回空列表）
     * @return 角色码列表（按插入顺序，首个为主角色），无分配时为空列表
     */
    public List<String> listRoles(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> codes = userRoleMapper.selectRoleCodesByUserId(userId);
        return codes == null ? Collections.emptyList() : codes;
    }

    /**
     * 规范化角色码集合：去空白、去重（保序）、剔除 role 表中不存在的非法码。
     *
     * @param roleCodes 原始角色码（可为 null）
     * @return 合法且去重后的角色码列表，永不为 null
     */
    public List<String> normalize(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> valid = roleMapper.selectList(null).stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());
        Set<String> ordered = new LinkedHashSet<>();
        for (String code : roleCodes) {
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            if (!trimmed.isEmpty() && valid.contains(trimmed)) {
                ordered.add(trimmed);
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 整体替换某用户的角色关系（先删后插，与 RoleService 分配权限同款语义）。
     *
     * @param userId    用户 id（为 null 直接返回）
     * @param roleCodes 目标角色码集合（调用方需先经 {@link #normalize}；空集合表示清空关系）
     */
    @Transactional
    public void replaceRoles(Long userId, List<String> roleCodes) {
        if (userId == null) {
            return;
        }
        userRoleMapper.deleteByUserId(userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        List<UserRole> rows = new ArrayList<>(roleCodes.size());
        for (String code : roleCodes) {
            UserRole row = new UserRole();
            row.setUserId(userId);
            row.setRoleCode(code);
            rows.add(row);
        }
        userRoleMapper.insertBatch(rows);
    }

    /**
     * 删除某用户的全部角色关系。
     *
     * @param userId 用户 id（为 null 直接返回）
     */
    @Transactional
    public void removeByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        userRoleMapper.deleteByUserId(userId);
    }
}
