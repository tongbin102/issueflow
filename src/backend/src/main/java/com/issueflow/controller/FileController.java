package com.issueflow.controller;

import com.issueflow.common.PageResult;
import com.issueflow.common.Result;
import com.issueflow.dto.req.FileConfigReq;
import com.issueflow.dto.req.FilePageReq;
import com.issueflow.dto.resp.FileConfigVO;
import com.issueflow.dto.resp.FileRecordVO;
import com.issueflow.service.FileConfigService;
import com.issueflow.service.FileRecordService;
import com.issueflow.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件管理控制器（Phase 7 T6，ARCH §2.2-32 中记为 {@code FileManageController}）。
 *
 * <p>路由前缀沿用 ARCH §3.8 的 {@code /api/admin/files}（前端页面路由才是
 * {@code /admin/infra/file}，两者不必一致）。</p>
 *
 * <p>权限校验统一在 Service 首行（ARCH §7.2），Controller 只做 DTO 绑定与 {@code @Valid}。
 * 各接口权限码见方法注释。</p>
 */
@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class FileController {

    private final FileRecordService fileRecordService;
    private final FileConfigService fileConfigService;

    /**
     * 文件列表分页（{@code file:list}）。
     *
     * @param req 分页与筛选请求
     * @return 分页结果（含 uploaderName / bizRef 批量回填）
     */
    @GetMapping
    public Result<PageResult<FileRecordVO>> page(FilePageReq req) {
        return Result.success(fileRecordService.pageQuery(req));
    }

    /**
     * 手工上传文件（{@code file:upload}，{@code bizType='MANUAL'}）。
     *
     * @param file 上传文件
     * @return 文件视图对象
     */
    @PostMapping
    public Result<FileRecordVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileRecordService.uploadManual(file, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 下载文件（{@code file:list}）：后端读流回传，{@code Content-Disposition: attachment}。
     *
     * @param id       文件 id
     * @param response HTTP 响应
     * @throws IOException 写流失败
     */
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        fileRecordService.download(id, response, false);
    }

    /**
     * 预览文件（{@code file:list}）：仅图片类，{@code inline} 内联展示。
     *
     * @param id       文件 id
     * @param response HTTP 响应
     * @throws IOException 写流失败
     */
    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws IOException {
        fileRecordService.download(id, response, true);
    }

    /**
     * 删除文件（{@code file:delete}）：软删记录 + 物理删除；
     * 物理删除失败不回滚，返回提示语告知需人工清理。
     *
     * @param id 文件 id
     * @return 结果提示语
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        return Result.success(fileRecordService.delete(id));
    }

    /**
     * 读取文件存储配置（{@code file:config}），含总占用与文件数。
     *
     * @return 配置视图
     */
    @GetMapping("/config")
    public Result<FileConfigVO> config() {
        return Result.success(fileConfigService.getConfigVO());
    }

    /**
     * 保存文件存储配置（{@code file:config}）：保存后 evict 缓存，对新上传立即生效。
     *
     * @param req 配置请求
     * @return 保存后的配置视图
     */
    @PutMapping("/config")
    public Result<FileConfigVO> saveConfig(@Valid @RequestBody FileConfigReq req) {
        return Result.success(fileConfigService.updateConfig(req));
    }
}
