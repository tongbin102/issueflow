package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.fieldconfig.req.FieldConfigReq;
import com.issueflow.dto.fieldconfig.resp.FieldConfigVO;
import com.issueflow.dto.fieldconfig.resp.FieldNodeVO;
import com.issueflow.dto.fieldconfig.resp.FieldSchemaVO;
import com.issueflow.dto.fieldconfig.resp.RefOptionVO;
import com.issueflow.dto.fieldconfig.resp.RefSourceVO;
import com.issueflow.dto.req.StatusToggleReq;
import com.issueflow.service.FieldConfigService;
import com.issueflow.service.RefSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字段配置控制器（ARCH §3.1，T03）。
 * <p>管理接口权限在 {@code FieldConfigService} / {@code RefSourceService} 内首行校验；
 * {@code /schema}、{@code /ref-options} 为登录即可（无权限码）。</p>
 * <p>路径复数 {@code /api/field-configs} 与项目既有资源根（dicts / issue-types / projects）一致（A2）。</p>
 */
@RestController
@RequestMapping("/api/field-configs")
@RequiredArgsConstructor
public class FieldConfigController {

    private final FieldConfigService fieldConfigService;
    private final RefSourceService refSourceService;

    /**
     * 表单渲染契约（登录即可）：sections + fields + systemTabs。
     *
     * @param typeScope 生效范围，本期恒 GLOBAL
     * @return schema 视图
     */
    @GetMapping("/schema")
    public Result<FieldSchemaVO> schema(@RequestParam(required = false) String typeScope) {
        return Result.success(fieldConfigService.getSchema(typeScope));
    }

    /**
     * REF 候选项（登录即可）：flat 返回 list / tree 返回树。
     *
     * @param refSource  白名单编码（必填）
     * @param parentValue 依赖源当前值（有值则按 filter_field 过滤）
     * @param keyword    模糊搜索
     * @return 候选列表
     */
    @GetMapping("/ref-options")
    public Result<List<RefOptionVO>> refOptions(@RequestParam String refSource,
                                                @RequestParam(required = false) String parentValue,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(refSourceService.query(refSource, parentValue, keyword));
    }

    /**
     * 管理页树形表格数据（field:config:list）：区域为父、字段为子。
     */
    @GetMapping("/tree")
    public Result<List<FieldNodeVO>> tree() {
        return Result.success(fieldConfigService.listTree());
    }

    /**
     * 配置页「引用表」下拉（field:config:list）：全部启用的引用源。
     */
    @GetMapping("/ref-sources")
    public Result<List<RefSourceVO>> refSources() {
        return Result.success(refSourceService.listEnabled());
    }

    /**
     * 字段详情（field:config:list）。
     */
    @GetMapping("/{id}")
    public Result<FieldConfigVO> detail(@PathVariable Long id) {
        return Result.success(fieldConfigService.detail(id));
    }

    /**
     * 新增字段（field:config:save）。
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FieldConfigReq req) {
        return Result.success(fieldConfigService.create(req));
    }

    /**
     * 编辑字段（field:config:save）；type 不一致抛 FIELD_TYPE_IMMUTABLE，内置字段仅放行白名单属性。
     * <p>不使用 {@code @Valid}：更新为部分字段语义（F12 内置字段仅回传白名单属性，{@code code}/{@code type}
     * 缺失属正常），字段校验与必填由 {@code FieldConfigService.update} 内部按 null 判定处理。</p>
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody FieldConfigReq req) {
        fieldConfigService.update(id, req);
        return Result.success();
    }

    /**
     * 删除字段（field:config:delete）；内置字段硬拦截。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fieldConfigService.delete(id);
        return Result.success();
    }

    /**
     * 字段启停切换（field:config:save）。
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @Valid @RequestBody StatusToggleReq req) {
        fieldConfigService.toggleStatus(id, req.getEnabled());
        return Result.success();
    }
}
