package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.MenuReq;
import com.issueflow.dto.resp.MenuVO;
import com.issueflow.entity.Menu;
import com.issueflow.mapper.MenuMapper;
import com.issueflow.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 菜单服务：扁平列表 + CRUD（删除前校验无子节点）
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuMapper menuMapper;

    /**
     * 全部菜单（按 sort,id 升序），前端组装树/层级
     */
    public List<MenuVO> listAll() {
        List<Menu> all = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getDeleted, 0)
                .orderByAsc(Menu::getSort)
                .orderByAsc(Menu::getId));
        return all.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 新建菜单
     */
    public MenuVO create(MenuReq req) {
        requireAdmin();
        if (req.getParentId() == null) {
            req.setParentId(0L);
        }
        if (req.getSort() == null) {
            req.setSort(0);
        }
        Menu menu = new Menu();
        menu.setName(req.getName());
        menu.setPath(req.getPath());
        menu.setParentId(req.getParentId());
        menu.setSort(req.getSort());
        menu.setPermission(req.getPermission());
        menu.setIcon(req.getIcon());
        menuMapper.insert(menu);
        return toVO(menu);
    }

    /**
     * 编辑菜单（仅更新非空字段）
     */
    public MenuVO update(Long id, MenuReq req) {
        requireAdmin();
        Menu exist = menuMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        if (req.getName() != null) {
            exist.setName(req.getName());
        }
        if (req.getPath() != null) {
            exist.setPath(req.getPath());
        }
        if (req.getParentId() != null) {
            if (Objects.equals(req.getParentId(), id)) {
                throw new BizException(ResultCode.VALID_ERROR, "父级不能为自身");
            }
            exist.setParentId(req.getParentId());
        }
        if (req.getSort() != null) {
            exist.setSort(req.getSort());
        }
        if (req.getPermission() != null) {
            exist.setPermission(req.getPermission());
        }
        if (req.getIcon() != null) {
            exist.setIcon(req.getIcon());
        }
        menuMapper.updateById(exist);
        return toVO(exist);
    }

    /**
     * 逻辑删除菜单（有子节点则禁止）
     */
    public void delete(Long id) {
        requireAdmin();
        Menu exist = menuMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        long childCount = menuMapper.selectCount(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getParentId, id)
                .eq(Menu::getDeleted, 0));
        if (childCount > 0) {
            throw new BizException(ResultCode.NODE_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
    }

    private MenuVO toVO(Menu m) {
        MenuVO vo = new MenuVO();
        vo.setId(m.getId());
        vo.setName(m.getName());
        vo.setPath(m.getPath());
        vo.setParentId(m.getParentId());
        vo.setSort(m.getSort());
        vo.setPermission(m.getPermission());
        vo.setIcon(m.getIcon());
        return vo;
    }

    private void requireAdmin() {
        if (!Constants.ROLE_ADMIN.equals(SecurityUtils.getCurrentRoleCode())) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
    }
}
