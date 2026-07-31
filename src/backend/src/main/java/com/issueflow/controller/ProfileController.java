package com.issueflow.controller;

import com.issueflow.common.PageResult;
import com.issueflow.common.Result;
import com.issueflow.dto.req.ActivityPageReq;
import com.issueflow.dto.req.BindingChangeReq;
import com.issueflow.dto.req.PasswordChangeReq;
import com.issueflow.dto.req.ProfileUpdateReq;
import com.issueflow.dto.resp.ActivityVO;
import com.issueflow.dto.resp.LoginLogVO;
import com.issueflow.dto.resp.ProfileVO;
import com.issueflow.service.LoginLogService;
import com.issueflow.service.ProfileService;
import com.issueflow.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 个人中心控制器（Phase 7 T5）。
 *
 * <p><b>全部接口「登录即可」，不设权限码</b>（ARCH §3.8）：个人中心是自助语义，
 * 给它加权限码反而会把「用户改自己密码」变成需要管理员授权的操作。</p>
 *
 * <p><b>越权设计</b>：路径与请求体中<b>都不出现 userId</b>，一律由 Service 从
 * SecurityContext 取当前登录用户；前端即便传了 userId 也不会被任何参数接收。
 * 唯一带 userId 的是头像读取端点 —— 展示他人头像是正常需求，且只返回图片字节。</p>
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final LoginLogService loginLogService;

    /**
     * 当前用户资料（登录即可）。
     *
     * @return 资料视图（email / phone 脱敏 + 原值分字段返回供编辑回填）
     */
    @GetMapping
    public Result<ProfileVO> profile() {
        return Result.success(profileService.profile());
    }

    /**
     * 编辑当前用户资料（登录即可）：昵称 / 姓名 / 邮箱 / 手机。
     *
     * @param req 编辑请求
     * @return 更新后的资料视图
     */
    @PutMapping
    public Result<ProfileVO> update(@Valid @RequestBody ProfileUpdateReq req) {
        return Result.success(profileService.updateProfile(req));
    }

    /**
     * 上传头像（登录即可）：图片类型强校验 + 2MB 上限。
     *
     * @param file 头像文件
     * @return 头像相对路径
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(profileService.uploadAvatar(file));
    }

    /**
     * 读取当前用户头像字节流（登录即可）。
     *
     * @param response HTTP 响应
     * @throws IOException 写流失败
     */
    @GetMapping("/avatar")
    public void myAvatar(HttpServletResponse response) throws IOException {
        profileService.writeAvatar(SecurityUtils.getCurrentUserId(), response);
    }

    /**
     * 读取指定用户头像字节流（登录即可，专用只读端点，ARCH §8.2）。
     *
     * <p>不复用 {@code /api/admin/files/{id}/preview} —— 后者需要 {@code file:list}
     * 权限，普通用户看同事头像不应该要管理员权限。</p>
     *
     * @param userId   目标用户 id
     * @param response HTTP 响应
     * @throws IOException 写流失败
     */
    @GetMapping("/avatar/{userId}")
    public void avatar(@PathVariable Long userId, HttpServletResponse response) throws IOException {
        profileService.writeAvatar(userId, response);
    }

    /**
     * 修改密码（登录即可）：原密码校验 + 强度校验 + 改密时间更新 + 当前 token 拉黑强制登出。
     *
     * @param req 改密请求
     * @return 空结果（前端收到成功后提示并跳登录页）
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeReq req) {
        profileService.changePassword(req);
        return Result.success();
    }

    /**
     * 变更手机 / 邮箱绑定（登录即可，需当前密码二次确认）。
     *
     * @param req 绑定变更请求
     * @return 更新后的资料视图
     */
    @PutMapping("/binding")
    public Result<ProfileVO> changeBinding(@Valid @RequestBody BindingChangeReq req) {
        return Result.success(profileService.changeBinding(req));
    }

    /**
     * 个人活动记录（登录即可）：登录日志 + 本人问题操作历史归并时间线。
     *
     * @param req 分页与筛选请求（type=ALL/LOGIN/ISSUE）
     * @return 分页结果
     */
    @GetMapping("/activities")
    public Result<PageResult<ActivityVO>> activities(ActivityPageReq req) {
        return Result.success(profileService.activities(req));
    }

    /**
     * 本人登录日志分页（登录即可）：活动记录页「登录」Tab 的精确分页数据源。
     *
     * @param page 页码，默认 1
     * @param size 每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/login-logs")
    public Result<PageResult<LoginLogVO>> loginLogs(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(loginLogService.pageMine(SecurityUtils.getCurrentUserId(), page, size));
    }
}
