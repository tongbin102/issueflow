package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.UserReq;
import com.issueflow.dto.resp.UserBriefVO;
import com.issueflow.dto.resp.UserVO;
import com.issueflow.entity.Role;
import com.issueflow.entity.User;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.UserMapper;
import com.issueflow.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户服务：认证查询 + 用户管理（增删改查/分页/密码加密）+ 姓名映射
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;

    /**
     * 根据用户名查询用户（不存在返回 null）
     */
    public User selectByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 根据 id 查询用户
     */
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 将 User 转换为 UserVO（补充角色码/角色名，隐去密码）
     */
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRoleId(user.getRoleId());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        if (user.getRoleId() != null) {
            Role role = roleMapper.selectById(user.getRoleId());
            if (role != null) {
                vo.setRoleCode(role.getCode());
                vo.setRoleName(role.getName());
            }
        }
        return vo;
    }

    /**
     * 分页查询用户（ADMIN）
     */
    public PageResult<UserVO> pageUsers(int pageNum, int size) {
        permissionService.requirePermission("user:list");
        Page<User> page = new Page<>(pageNum, size);
        userMapper.selectPage(page, new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));
        List<UserVO> list = page.getRecords().stream().map(this::getUserVO).collect(Collectors.toList());
        return PageResult.of(list, page.getTotal(), (long) pageNum, (long) size);
    }

    /**
     * 新增用户（密码 BCrypt 加密）
     */
    @Transactional
    public UserVO createUser(UserReq req) {
        permissionService.requirePermission("user:create");
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) > 0) {
            throw new BizException(ResultCode.VALID_ERROR, "用户名已存在");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "密码不能为空");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setRoleId(req.getRoleId());
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        userMapper.insert(user);
        return getUserVO(user);
    }

    /**
     * 编辑用户（密码为空则保持原密码）
     */
    @Transactional
    public UserVO updateUser(Long id, UserReq req) {
        permissionService.requirePermission("user:update");
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setUsername(req.getUsername());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        user.setRealName(req.getRealName());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setRoleId(req.getRoleId());
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        userMapper.updateById(user);
        return getUserVO(user);
    }

    /**
     * 逻辑删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        permissionService.requirePermission("user:delete");
        userMapper.deleteById(id);
    }

    /**
     * 构建 userId -> 显示名（realName 优先，缺省用 username）映射，供 VO 反查姓名
     */
    public Map<Long, String> userNameMap() {
        List<User> users = userMapper.selectList(null);
        Map<Long, String> map = new HashMap<>(users.size() + 8);
        for (User user : users) {
            String name = (user.getRealName() != null && !user.getRealName().isBlank())
                    ? user.getRealName() : user.getUsername();
            map.put(user.getId(), name);
        }
        return map;
    }

    /**
     * 用户下拉选项（仅登录）：返回 status=1 & deleted=0 的用户，
     * 按 real_name / username 模糊匹配，上限 100 条，回填 roleName。
     *
     * @param keyword 可选模糊关键字（匹配 real_name 或 username）
     * @return 用户简览列表
     */
    public List<UserBriefVO> listUserOptions(String keyword) {
        Page<User> pg = new Page<>(1, 100);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 1).eq(User::getDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getRealName, kw).or().like(User::getUsername, kw));
        }
        userMapper.selectPage(pg, wrapper);

        Map<Long, String> roleMap = new HashMap<>();
        roleMapper.selectList(null).forEach(r -> roleMap.put(r.getId(), r.getName()));

        List<UserBriefVO> result = new ArrayList<>();
        for (User u : pg.getRecords()) {
            UserBriefVO vo = new UserBriefVO();
            vo.setId(u.getId());
            vo.setRealName(u.getRealName());
            vo.setUsername(u.getUsername());
            vo.setRoleName(roleMap.get(u.getRoleId()));
            result.add(vo);
        }
        return result;
    }
}
