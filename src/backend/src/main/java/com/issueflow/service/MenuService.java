package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.MenuReq;
import com.issueflow.dto.resp.MenuNodeVO;
import com.issueflow.dto.resp.MenuVO;
import com.issueflow.entity.Menu;
import com.issueflow.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 菜单服务：扁平列表（按端过滤）+ 树（按端组装）+ CRUD（删除前校验无子节点）。
 * 写操作统一走 PermissionService.requirePermission 鉴权（ADMIN 放行）。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuMapper menuMapper;
    private final PermissionService permissionService;

    /**
     * 全部菜单（按 sort,id 升序），前端组装树/层级（含 type）
     */
    public List<MenuVO> listAll() {
        List<Menu> all = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getDeleted, 0)
                .orderByAsc(Menu::getSort)
                .orderByAsc(Menu::getId));
        return all.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按端过滤的扁平菜单列表（type 可空，空则不过滤）
     */
    public List<MenuVO> listByType(Integer type) {
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<Menu>().eq(Menu::getDeleted, 0);
        if (type != null) {
            wrapper.eq(Menu::getType, type);
        }
        wrapper.orderByAsc(Menu::getSort).orderByAsc(Menu::getId);
        return menuMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按端组装菜单树（供前端侧栏动态渲染）。type 必填（通常 1 前台 / 2 后台）。
     */
    public List<MenuNodeVO> listSidebarTree(Integer type) {
        List<Menu> all = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getDeleted, 0)
                .eq(type != null, Menu::getType, type)
                .orderByAsc(Menu::getSort)
                .orderByAsc(Menu::getId));

        Map<Long, MenuNodeVO> nodeMap = new LinkedHashMap<>();
        for (Menu m : all) {
            nodeMap.put(m.getId(), toNode(m));
        }
        List<MenuNodeVO> roots = new ArrayList<>();
        for (MenuNodeVO node : nodeMap.values()) {
            Long pid = node.getParentId();
            if (pid != null && pid != 0 && nodeMap.containsKey(pid)) {
                nodeMap.get(pid).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 新建菜单
     */
    public MenuVO create(MenuReq req) {
        permissionService.requirePermission("menu:create");
        if (req.getParentId() == null) {
            req.setParentId(0L);
        }
        if (req.getSort() == null) {
            req.setSort(0);
        }
        if (req.getType() == null) {
            req.setType(Constants.MENU_TYPE_ADMIN);
        }
        Menu menu = new Menu();
        menu.setName(req.getName());
        menu.setPath(req.getPath());
        menu.setParentId(req.getParentId());
        menu.setSort(req.getSort());
        menu.setPermission(req.getPermission());
        menu.setIcon(req.getIcon());
        menu.setType(req.getType());
        menuMapper.insert(menu);
        return toVO(menu);
    }

    /**
     * 编辑菜单（仅更新非空字段）
     */
    public MenuVO update(Long id, MenuReq req) {
        permissionService.requirePermission("menu:update");
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
        if (req.getType() != null) {
            exist.setType(req.getType());
        }
        menuMapper.updateById(exist);
        return toVO(exist);
    }

    /**
     * 逻辑删除菜单（有子节点则禁止）
     */
    public void delete(Long id) {
        permissionService.requirePermission("menu:delete");
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
        vo.setType(m.getType());
        return vo;
    }

    private MenuNodeVO toNode(Menu m) {
        MenuNodeVO node = new MenuNodeVO();
        node.setId(m.getId());
        node.setName(m.getName());
        node.setPath(m.getPath());
        node.setParentId(m.getParentId());
        node.setSort(m.getSort());
        node.setPermission(m.getPermission());
        node.setIcon(m.getIcon());
        node.setType(m.getType());
        node.setChildren(new ArrayList<>());
        return node;
    }
}
