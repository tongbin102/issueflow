package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.DictItemReq;
import com.issueflow.dto.req.DictTypeReq;
import com.issueflow.dto.req.StatusToggleReq;
import com.issueflow.dto.resp.DictItemVO;
import com.issueflow.dto.resp.DictOptionVO;
import com.issueflow.dto.resp.DictTypeVO;
import com.issueflow.service.DictService;
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
 * 字典配置控制器：类型 CRUD + 项 CRUD + 下拉选项 + 启停（ARCH §3.6，10 个接口）
 * <p>管理接口需对应权限码（在 {@code DictService} 内首行校验）；
 * {@code /options} 为登录即可访问的全站下拉唯一数据源。</p>
 */
@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /**
     * 字典类型管理列表（dict:list）
     *
     * @param keyword 关键字，匹配编码或名称
     * @param enabled 启用态过滤：1 启用 / 0 停用
     * @return 类型列表
     */
    @GetMapping("/types")
    public Result<List<DictTypeVO>> listTypes(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer enabled) {
        return Result.success(dictService.listTypes(keyword, enabled));
    }

    /**
     * 新增字典类型（dict:create）
     *
     * @param req 类型入参
     * @return 新类型主键
     */
    @PostMapping("/types")
    public Result<Long> createType(@Valid @RequestBody DictTypeReq req) {
        return Result.success(dictService.createType(req));
    }

    /**
     * 编辑字典类型（dict:update；编码不可改，服务端忽略 code）
     *
     * @param id  类型主键
     * @param req 类型入参
     * @return 空结果
     */
    @PutMapping("/types/{id}")
    public Result<Void> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeReq req) {
        dictService.updateType(id, req);
        return Result.success();
    }

    /**
     * 字典类型启停（dict:update）
     *
     * @param id  类型主键
     * @param req 启停入参
     * @return 空结果
     */
    @PutMapping("/types/{id}/status")
    public Result<Void> toggleTypeStatus(@PathVariable Long id, @Valid @RequestBody StatusToggleReq req) {
        dictService.toggleTypeStatus(id, req.getEnabled());
        return Result.success();
    }

    /**
     * 删除字典类型（dict:delete；系统预设 / 仍有选项时硬拦截）
     *
     * @param id 类型主键
     * @return 空结果
     */
    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.success();
    }

    /**
     * 字典项管理列表（dict:list，含 refCount）
     *
     * @param typeCode 字典类型编码
     * @param keyword  关键字
     * @param enabled  启用态过滤
     * @return 字典项列表
     */
    @GetMapping("/items")
    public Result<List<DictItemVO>> listItems(@RequestParam String typeCode,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer enabled) {
        return Result.success(dictService.listItems(typeCode, keyword, enabled));
    }

    /**
     * 新增字典项（dict:create；镜像类型禁止新增）
     *
     * @param req 字典项入参
     * @return 新字典项主键
     */
    @PostMapping("/items")
    public Result<Long> createItem(@Valid @RequestBody DictItemReq req) {
        return Result.success(dictService.createItem(req));
    }

    /**
     * 编辑字典项（dict:update；预设项 code 静默忽略）
     *
     * @param id  字典项主键
     * @param req 字典项入参
     * @return 空结果
     */
    @PutMapping("/items/{id}")
    public Result<Void> updateItem(@PathVariable Long id, @Valid @RequestBody DictItemReq req) {
        dictService.updateItem(id, req);
        return Result.success();
    }

    /**
     * 字典项启停（dict:update）
     *
     * @param id  字典项主键
     * @param req 启停入参
     * @return 空结果
     */
    @PutMapping("/items/{id}/status")
    public Result<Void> toggleItemStatus(@PathVariable Long id, @Valid @RequestBody StatusToggleReq req) {
        dictService.toggleItemStatus(id, req.getEnabled());
        return Result.success();
    }

    /**
     * 删除字典项（dict:delete；系统预设项 / 被问题引用时硬拦截，仅可停用）
     *
     * @param id 字典项主键
     * @return 空结果
     */
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.success();
    }

    /**
     * 全站下拉唯一数据源（登录即可）：命中两级缓存，0 次 DB 查询。
     *
     * @param typeCode        字典类型编码，如 ISSUE_SOURCE
     * @param includeDisabled 是否包含停用项（筛选场景传 true，停用项自动置底）
     * @return 下拉选项列表
     */
    @GetMapping("/options")
    public Result<List<DictOptionVO>> options(@RequestParam String typeCode,
                                              @RequestParam(required = false, defaultValue = "false")
                                              Boolean includeDisabled) {
        return Result.success(dictService.options(typeCode, includeDisabled));
    }
}
