package com.issueflow.dto.resp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点视图对象（用于侧栏动态渲染）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuNodeVO extends MenuVO {

    private static final long serialVersionUID = 1L;

    /** 子节点 */
    private List<MenuNodeVO> children = new ArrayList<>();
}
