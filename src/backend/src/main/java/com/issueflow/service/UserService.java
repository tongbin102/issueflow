package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.UserReq;
import com.issueflow.dto.resp.UserBriefVO;
import com.issueflow.dto.resp.UserVO;
import com.issueflow.entity.Organization;
import com.issueflow.entity.Role;
import com.issueflow.entity.User;
import com.issueflow.enums.EnableStatusEnum;
import com.issueflow.mapper.OrganizationMapper;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.UserMapper;
import com.issueflow.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
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
    /** Phase8 W2 #9：组织名称反查。注入 Mapper 而非 OrganizationService，避免与其反向依赖成环 */
    private final OrganizationMapper organizationMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    /** Phase8 W2 #7：新增用户密码留空时读取 site.default_password */
    private final SiteConfigService siteConfigService;
    /** Phase8 W3 #11：多角色关系表读写 */
    private final UserRoleService userRoleService;

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
     * 邮箱是否已被其他账号占用（逻辑删除记录不计）。
     *
     * @param email          待校验邮箱（为 null 直接返回 false）
     * @param excludeUserId  排除的当前用户 id（编辑/绑定自身时跳过）
     * @return 存在其他账号占用则为 true
     */
    public boolean existsEmail(String email, Long excludeUserId) {
        if (email == null || email.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email).eq(User::getDeleted, 0);
        if (excludeUserId != null) {
            wrapper.ne(User::getId, excludeUserId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    /**
     * 手机号是否已被其他账号占用（逻辑删除记录不计）。
     *
     * @param phone          待校验手机号（为 null 直接返回 false）
     * @param excludeUserId  排除的当前用户 id（编辑/绑定自身时跳过）
     * @return 存在其他账号占用则为 true
     */
    public boolean existsPhone(String phone, Long excludeUserId) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone).eq(User::getDeleted, 0);
        if (excludeUserId != null) {
            wrapper.ne(User::getId, excludeUserId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    /**
     * 解析用户的全部角色码（Phase8 W3 #11 新增，多角色统一入口）。
     *
     * <p>取值优先级：{@code user.roles}（JSON 冗余列，免查询）→ {@code user_role} 关系表
     * → {@code user.role_id} 对应的单角色码（存量数据未回填时的兜底）。</p>
     *
     * @param user 用户实体（为 null 返回空列表）
     * @return 角色码列表，首位为主角色，永不为 null
     */
    public List<String> resolveRoleCodes(User user) {
        if (user == null) {
            return Collections.emptyList();
        }
        List<String> codes = user.getRoles();
        if (codes == null || codes.isEmpty()) {
            codes = userRoleService.listRoles(user.getId());
        }
        if (codes == null || codes.isEmpty()) {
            if (user.getRoleId() == null) {
                return Collections.emptyList();
            }
            Role role = roleMapper.selectById(user.getRoleId());
            return role == null ? Collections.emptyList() : Collections.singletonList(role.getCode());
        }
        return new ArrayList<>(codes);
    }

    /**
     * 查询指定用户的全部角色码（供 GET /api/users/{id}/roles 编辑回显）。
     *
     * @param id 用户 id
     * @return 角色码列表
     */
    public List<String> listUserRoleCodes(Long id) {
        permissionService.requirePermission("user:list");
        return resolveRoleCodes(userMapper.selectById(id));
    }

    /**
     * 解析请求中的角色分配：校验、去重、对齐主角色。
     *
     * <p>兼容两种入参：只传 {@code roles}（新前端）、只传 {@code roleId}（历史调用方）。
     * 主角色 {@code roleId} 始终与角色码列表首位保持一致。</p>
     *
     * @param req 用户请求
     * @return 角色分配结果（主角色 id + 角色码列表）
     * @throws BizException 角色为空或全部非法时抛出
     */
    private RoleAssignment resolveRoleAssignment(UserReq req) {
        List<Role> allRoles = roleMapper.selectList(null);
        List<String> codes = userRoleService.normalize(req.getRoles());
        if (codes.isEmpty() && req.getRoleId() != null) {
            // 历史调用方只传 roleId：退化为单角色
            for (Role role : allRoles) {
                if (Objects.equals(role.getId(), req.getRoleId())) {
                    codes = new ArrayList<>(Collections.singletonList(role.getCode()));
                    break;
                }
            }
        }
        if (codes.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "角色不能为空");
        }
        // 主角色：优先沿用请求里的 roleId（需在所选角色内），否则取首个角色码对应的 id
        Long primaryRoleId = null;
        for (Role role : allRoles) {
            if (Objects.equals(role.getId(), req.getRoleId()) && codes.contains(role.getCode())) {
                primaryRoleId = role.getId();
                break;
            }
        }
        if (primaryRoleId == null) {
            String primaryCode = codes.get(0);
            for (Role role : allRoles) {
                if (primaryCode.equals(role.getCode())) {
                    primaryRoleId = role.getId();
                    break;
                }
            }
        }
        if (primaryRoleId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "角色不存在");
        }
        return new RoleAssignment(primaryRoleId, codes);
    }

    /** 角色分配结果：主角色 id + 全部角色码（首位与主角色一致） */
    private record RoleAssignment(Long roleId, List<String> roleCodes) {
    }

    /**
     * 将 User 转换为 UserVO（补充角色码/角色名/全部角色码，隐去密码）
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
        // Phase8 W2 #9：所属组织（可空，组织已被删除时仅回填 id，名称留空）
        vo.setOrgId(user.getOrgId());
        if (user.getOrgId() != null) {
            Organization org = organizationMapper.selectById(user.getOrgId());
            if (org != null) {
                vo.setOrgName(org.getName());
            }
        }
        vo.setLeaderId(user.getLeaderId());
        if (user.getLeaderId() != null) {
            User leader = userMapper.selectById(user.getLeaderId());
            if (leader != null) {
                String leaderName = (leader.getRealName() != null && !leader.getRealName().isBlank())
                        ? leader.getRealName() : leader.getUsername();
                vo.setLeaderName(leaderName);
            }
        }
        vo.setStatus(user.getStatus());
        vo.setAvatar(user.getAvatar());
        vo.setNickname(user.getNickname());
        vo.setBindWechat(user.getBindWechat());
        vo.setBindDingtalk(user.getBindDingtalk());
        vo.setCreatedAt(user.getCreatedAt());
        if (user.getRoleId() != null) {
            Role role = roleMapper.selectById(user.getRoleId());
            if (role != null) {
                vo.setRoleCode(role.getCode());
                vo.setRoleName(role.getName());
            }
        }
        // Phase8 W3 #11：全部角色码（单角色用户为单元素列表，保证前端可统一按数组消费）
        vo.setRoles(resolveRoleCodes(user));
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
     * 新增用户（密码 BCrypt 加密）。
     *
     * <p>Phase8 W2 #7：密码不再由前端录入——请求里 password 为空/空白时，
     * 取「系统设置」中的 {@code site.default_password} 作为初始密码，再 BCrypt 加密落库。
     * 明文默认密码只在服务端内部流转，绝不回传给前端。</p>
     */
    @Transactional
    public UserVO createUser(UserReq req) {
        permissionService.requirePermission("user:create");
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) > 0) {
            throw new BizException(ResultCode.VALID_ERROR, "用户名已存在");
        }
        String rawPassword = req.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = siteConfigService.getDefaultUserPassword();
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            // 兜底：配置被清空且常量缺省时不允许创建空密码账号
            throw new BizException(ResultCode.VALID_ERROR, "密码不能为空");
        }
        // Phase8 W3 #11：解析多角色（roles 优先，退化兼容仅传 roleId 的调用方）
        RoleAssignment assignment = resolveRoleAssignment(req);
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRealName(req.getRealName());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setAvatar(req.getAvatar());
        user.setNickname(req.getNickname());
        user.setRoleId(assignment.roleId());
        user.setRoles(assignment.roleCodes());
        // Phase8 W2 #9：所属组织（可空）
        user.setOrgId(req.getOrgId());
        user.setLeaderId(req.getLeaderId());
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        userMapper.insert(user);
        // Phase8 W3 #11：落 user_role 关系（整体替换）
        userRoleService.replaceRoles(user.getId(), assignment.roleCodes());
        // 极端场景防环：新建后若上级指向了自己（前端不会出现），置空修正
        if (user.getLeaderId() != null && Objects.equals(user.getLeaderId(), user.getId())) {
            user.setLeaderId(null);
            userMapper.updateById(user);
        }
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
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        // Phase8 W3 #11：多角色「整体替换」——主角色与角色码列表一并对齐
        RoleAssignment assignment = resolveRoleAssignment(req);
        user.setRoleId(assignment.roleId());
        user.setRoles(assignment.roleCodes());
        // Phase8 W2 #9：所属组织「存在即覆盖」——传 null 表示解除组织归属
        user.setOrgId(req.getOrgId());
        if (req.getLeaderId() != null && Objects.equals(req.getLeaderId(), id)) {
            throw new BizException(ResultCode.USER_LEADER_CYCLE);
        }
        user.setLeaderId(req.getLeaderId());
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        userMapper.updateById(user);
        userRoleService.replaceRoles(id, assignment.roleCodes());
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
        wrapper.eq(User::getStatus, EnableStatusEnum.ENABLED.getCode()).eq(User::getDeleted, 0);
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
