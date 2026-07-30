package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 模块简要视图对象（依赖列表悬浮展示用）
 */
@Data
public class ModuleBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;
}
