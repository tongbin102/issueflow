package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.ProjectReq;
import com.issueflow.dto.resp.ProjectOptionVO;
import com.issueflow.dto.resp.ProjectVO;
import com.issueflow.dto.resp.UserBriefVO;
import com.issueflow.entity.Issue;
import com.issueflow.entity.Project;
import com.issueflow.entity.Role;
import com.issueflow.entity.User;
import com.issueflow.enums.EnableStatusEnum;
import com.issueflow.enums.IssueStatusEnum;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.mapper.ProjectMapper;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.UserMapper;
import com.issueflow.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目服务：CRUD + 分页 + 下拉选项 + 负责人/成员批量回填（避免 N+1）。
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final IssueMapper issueMapper;
    private final PermissionService permissionService;

    /**
     * 项目分页（按创建时间倒序）。
     * 当页所有 leaderId + 切分 memberIds 汇总后一次性 selectBatchIds 回填，禁止逐行查 user（N+1）。
     */
    public PageResult<ProjectVO> pageProjects(int page, int size) {
        permissionService.requirePermission("project:list");
        Page<Project> pg = new Page<>(page, size);
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Project::getCreatedAt);
        projectMapper.selectPage(pg, wrapper);

        // 汇总当页所有 leaderId + memberIds 中的 userId，去重
        Set<Long> userIds = new HashSet<>();
        for (Project p : pg.getRecords()) {
            if (p.getLeaderId() != null) {
                userIds.add(p.getLeaderId());
            }
            collectMemberIds(p.getMemberIds(), userIds);
        }
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        // roleId -> roleName 映射
        Map<Long, String> roleMap = buildRoleMap();

        List<ProjectVO> list = pg.getRecords().stream()
                .map(p -> toVO(p, userMap, roleMap))
                .collect(Collectors.toList());
        return PageResult.of(list, pg.getTotal(), (long) page, (long) size);
    }

    /**
     * 新建项目（名称唯一校验）；写入 leaderId / memberIds。
     */
    @Transactional
    public ProjectVO createProject(ProjectReq req) {
        permissionService.requirePermission("project:create");
        if (projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getName, req.getName())
                .eq(Project::getDeleted, 0)) > 0) {
            throw new BizException(ResultCode.PROJECT_NAME_DUPLICATE);
        }
        Project project = new Project();
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        project.setLeaderId(req.getLeaderId());
        project.setMemberIds(req.getMemberIds());
        projectMapper.insert(project);
        return toVOWithUsers(project);
    }

    /**
     * 编辑项目（仅更新非空字段，名称变更时校验唯一）。
     * leaderId / memberIds 采用「存在即覆盖」（不过滤 null，由前端发送完整 payload）。
     * 停用校验（R4）：status 由 1→0 且存在未关闭问题时禁止停用。
     */
    @Transactional
    public ProjectVO updateProject(Long id, ProjectReq req) {
        permissionService.requirePermission("project:update");
        Project exist = projectMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }

        // R4：启用 → 停用 方向校验
        // isDisabled/isEnabled 均为 null-safe，等价于原「req.status != null && req.status == 0 && exist.status == 1」
        if (EnableStatusEnum.isDisabled(req.getStatus()) && EnableStatusEnum.isEnabled(exist.getStatus())) {
            long openCount = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getProjectId, id)
                    .ne(Issue::getStatus, IssueStatusEnum.CLOSED.getCode())
                    .eq(Issue::getDeleted, 0));
            if (openCount > 0) {
                throw new BizException(ResultCode.PROJECT_HAS_OPEN_ISSUES,
                        "该项目下存在未关闭问题，无法停用");
            }
        }

        if (req.getName() != null && !Objects.equals(exist.getName(), req.getName())) {
            if (projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                    .eq(Project::getName, req.getName())
                    .eq(Project::getDeleted, 0)) > 0) {
                throw new BizException(ResultCode.PROJECT_NAME_DUPLICATE);
            }
            exist.setName(req.getName());
        }
        if (req.getDescription() != null) {
            exist.setDescription(req.getDescription());
        }
        if (req.getStatus() != null) {
            exist.setStatus(req.getStatus());
        }
        // 负责人 / 成员：存在即覆盖（前端发送完整 payload，避免切状态时清空）
        exist.setLeaderId(req.getLeaderId());
        exist.setMemberIds(req.getMemberIds());

        projectMapper.updateById(exist);
        return toVOWithUsers(exist);
    }

    /**
     * 逻辑删除项目
     */
    public void deleteProject(Long id) {
        permissionService.requirePermission("project:delete");
        Project exist = projectMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        projectMapper.deleteById(id);
    }

    /**
     * 下拉选项（R3：仅返回 status=1 的项目）。
     */
    public List<ProjectOptionVO> listOptions() {
        List<Project> all = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getDeleted, 0)
                .eq(Project::getStatus, EnableStatusEnum.ENABLED.getCode())
                .orderByDesc(Project::getCreatedAt));
        return all.stream().map(p -> {
            ProjectOptionVO vo = new ProjectOptionVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setStatus(p.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * id -> 名称 映射（用于 IssueVO 回显 projectName）
     */
    public Map<Long, String> nameMap() {
        List<Project> all = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getDeleted, 0));
        return all.stream().collect(Collectors.toMap(
                Project::getId, Project::getName, (a, b) -> a));
    }

    /**
     * 构建 roleId -> roleName 映射
     */
    private Map<Long, String> buildRoleMap() {
        Map<Long, String> roleMap = new HashMap<>();
        roleMapper.selectList(null).forEach(r -> roleMap.put(r.getId(), r.getName()));
        return roleMap;
    }

    /**
     * 收集 memberIds 中的 userId 到 out（逗号分隔，丢弃脏数据）
     */
    private void collectMemberIds(String memberIds, Set<Long> out) {
        if (memberIds == null || memberIds.isBlank()) {
            return;
        }
        for (String s : memberIds.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(t));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过
            }
        }
    }

    /**
     * 由实体构建完整 VO：补齐 userMap 后回填 leaderName / members
     */
    private ProjectVO toVOWithUsers(Project p) {
        Set<Long> ids = new HashSet<>();
        if (p.getLeaderId() != null) {
            ids.add(p.getLeaderId());
        }
        collectMemberIds(p.getMemberIds(), ids);
        Map<Long, User> userMap = new HashMap<>();
        if (!ids.isEmpty()) {
            userMapper.selectBatchIds(ids).forEach(u -> userMap.put(u.getId(), u));
        }
        return toVO(p, userMap, buildRoleMap());
    }

    /**
     * 实体 -> VO，回填 leaderName 与 members（按 memberIds 顺序，丢弃无效 id）
     */
    private ProjectVO toVO(Project p, Map<Long, User> userMap, Map<Long, String> roleMap) {
        ProjectVO vo = new ProjectVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setStatus(p.getStatus());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        vo.setLeaderId(p.getLeaderId());
        vo.setMemberIds(p.getMemberIds());

        if (p.getLeaderId() != null) {
            User leader = userMap.get(p.getLeaderId());
            if (leader != null) {
                vo.setLeaderName(leader.getRealName() != null && !leader.getRealName().isBlank()
                        ? leader.getRealName() : leader.getUsername());
            }
        }

        if (p.getMemberIds() != null && !p.getMemberIds().isBlank()) {
            List<UserBriefVO> members = new ArrayList<>();
            for (String s : p.getMemberIds().split(",")) {
                String t = s.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    Long uid = Long.parseLong(t);
                    User u = userMap.get(uid);
                    if (u != null) {
                        UserBriefVO b = new UserBriefVO();
                        b.setId(u.getId());
                        b.setRealName(u.getRealName());
                        b.setUsername(u.getUsername());
                        b.setRoleName(roleMap.get(u.getRoleId()));
                        members.add(b);
                    }
                } catch (NumberFormatException ignored) {
                    // 脏数据跳过
                }
            }
            vo.setMembers(members);
        }
        return vo;
    }
}
