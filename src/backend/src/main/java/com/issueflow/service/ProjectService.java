package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.ProjectReq;
import com.issueflow.dto.resp.ProjectOptionVO;
import com.issueflow.dto.resp.ProjectVO;
import com.issueflow.entity.Project;
import com.issueflow.mapper.ProjectMapper;
import com.issueflow.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 项目服务：CRUD + 分页 + 下拉选项 + 名称映射
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final PermissionService permissionService;

    /**
     * 项目分页（按创建时间倒序）
     */
    public PageResult<ProjectVO> pageProjects(int page, int size) {
        permissionService.requirePermission("project:list");
        Page<Project> pg = new Page<>(page, size);
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Project::getCreatedAt);
        projectMapper.selectPage(pg, wrapper);
        List<ProjectVO> list = pg.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(list, pg.getTotal(), (long) page, (long) size);
    }

    /**
     * 新建项目（名称唯一校验）
     */
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
        projectMapper.insert(project);
        return toVO(project);
    }

    /**
     * 编辑项目（仅更新非空字段，名称变更时校验唯一）
     */
    public ProjectVO updateProject(Long id, ProjectReq req) {
        permissionService.requirePermission("project:update");
        Project exist = projectMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
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
        projectMapper.updateById(exist);
        return toVO(exist);
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
     * 下拉选项（全部未删除项目，含 status 供前端置灰）
     */
    public List<ProjectOptionVO> listOptions() {
        List<Project> all = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getDeleted, 0)
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

    private ProjectVO toVO(Project p) {
        ProjectVO vo = new ProjectVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setStatus(p.getStatus());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
