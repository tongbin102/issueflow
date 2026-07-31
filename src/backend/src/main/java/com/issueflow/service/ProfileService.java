package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.ActivityPageReq;
import com.issueflow.dto.req.BindingChangeReq;
import com.issueflow.dto.req.PasswordChangeReq;
import com.issueflow.dto.req.ProfileUpdateReq;
import com.issueflow.dto.resp.ActivityVO;
import com.issueflow.dto.resp.ProfileVO;
import com.issueflow.entity.FileRecord;
import com.issueflow.entity.LoginLog;
import com.issueflow.entity.Organization;
import com.issueflow.entity.Role;
import com.issueflow.entity.User;
import com.issueflow.enums.HistoryActionEnum;
import com.issueflow.mapper.IssueHistoryMapper;
import com.issueflow.mapper.OrganizationMapper;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.UserMapper;
import com.issueflow.security.JwtUtil;
import com.issueflow.util.FileUtil;
import com.issueflow.util.MaskUtils;
import com.issueflow.util.SecurityUtils;
import com.issueflow.util.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 个人中心服务（Phase 7 T5）。
 *
 * <p><b>越权结构性杜绝</b>（ARCH §1.4-5 / §7.8）：本类<b>任何方法都不接受 userId 入参</b>，
 * 一律 {@link SecurityUtils#getCurrentUserId()}。这比「接收 userId 再校验相等」更稳 ——
 * 后者只要有一处忘了校验就是越权漏洞，前者从签名层面让越权无从表达。
 * 唯一例外是头像读取端点需要 userId（展示他人头像属正常需求，且只返回图片字节）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    /** 活动记录两路取数的单路上限，防止深翻页把内存打满（ARCH §8.4） */
    private static final int ACTIVITY_FETCH_CAP = 1000;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    /** Phase8 W2 #9：user.org_id 落库后用于回填组织名称 */
    private final OrganizationMapper organizationMapper;
    private final UserService userService;
    private final FileRecordService fileRecordService;
    private final FileConfigService fileConfigService;
    private final LoginLogService loginLogService;
    private final IssueHistoryMapper issueHistoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    // ============================ 基本信息 ============================

    /**
     * 当前登录用户资料。
     *
     * @return 资料视图（email / phone 脱敏，另附 emailRaw / phoneRaw 供编辑回填）
     */
    public ProfileVO profile() {
        User user = currentUser();
        ProfileVO vo = new ProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRealName(user.getRealName());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(MaskUtils.maskEmail(user.getEmail()));
        vo.setPhone(MaskUtils.maskPhone(user.getPhone()));
        vo.setEmailRaw(user.getEmail());
        vo.setPhoneRaw(user.getPhone());
        // Phase8 W2 #9：user.org_id 已落库（V20260802），据此回填组织名称。
        // 未归属组织、或组织已被删除时保持 null，不做假数据。
        if (user.getOrgId() != null) {
            Organization org = organizationMapper.selectById(user.getOrgId());
            vo.setOrgName(org == null ? null : org.getName());
        } else {
            vo.setOrgName(null);
        }
        if (user.getRoleId() != null) {
            Role role = roleMapper.selectById(user.getRoleId());
            if (role != null) {
                vo.setRoleName(role.getName());
                vo.setRoleCode(role.getCode());
            }
        }
        vo.setCreatedAt(user.getCreatedAt());
        vo.setPwdUpdatedAt(user.getPwdUpdatedAt());
        return vo;
    }

    /**
     * 编辑当前用户资料（昵称 / 姓名 / 邮箱 / 手机）。
     *
     * <p>邮箱与手机做全局唯一性校验：本期库上<b>没有</b>唯一索引（ARCH §8.6），
     * 唯一性完全靠这里的 Service 校验兜底，不可省略。</p>
     *
     * @param req 编辑请求
     * @return 更新后的资料视图
     */
    @Transactional
    public ProfileVO updateProfile(ProfileUpdateReq req) {
        User user = currentUser();
        String email = trimToNull(req.getEmail());
        String phone = trimToNull(req.getPhone());
        if (email != null && userService.existsEmail(email, user.getId())) {
            throw new BizException(ResultCode.VALID_ERROR, "该邮箱已被其他账号使用");
        }
        if (phone != null && userService.existsPhone(phone, user.getId())) {
            throw new BizException(ResultCode.VALID_ERROR, "该手机号已被其他账号使用");
        }
        User patch = new User();
        patch.setId(user.getId());
        patch.setNickname(trimToNull(req.getNickname()));
        patch.setRealName(trimToNull(req.getRealName()));
        patch.setEmail(email);
        patch.setPhone(phone);
        userMapper.updateById(patch);
        return profile();
    }

    // ============================ 密码与绑定 ============================

    /**
     * 修改密码：原密码校验 → 强度校验 → 更新 → <b>当前 token 拉黑（强制登出）</b>。
     *
     * <p>强制登出采纳 PRD Q5 方案 A，复用 Phase 1 已有的 {@code jwt:blacklist:{jti}} 机制。
     * 拉黑动作放在事务提交语义之外无所谓 —— 即便拉黑失败，密码也已改成，
     * 旧 token 最迟在 2 小时后自然过期。</p>
     *
     * @param req 改密请求
     */
    @Transactional
    public void changePassword(PasswordChangeReq req) {
        User user = currentUser();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.VALID_ERROR, "原密码不正确");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BizException(ResultCode.VALID_ERROR, "两次输入的新密码不一致");
        }
        if (req.getNewPassword().equals(req.getOldPassword())) {
            throw new BizException(ResultCode.VALID_ERROR, "新密码不能与原密码相同");
        }
        User patch = new User();
        patch.setId(user.getId());
        patch.setPassword(passwordEncoder.encode(req.getNewPassword()));
        patch.setPwdUpdatedAt(LocalDateTime.now());
        userMapper.updateById(patch);
        blacklistCurrentToken();
    }

    /**
     * 变更手机 / 邮箱绑定（需当前密码二次确认）。
     *
     * @param req 绑定变更请求
     * @return 更新后的资料视图
     */
    @Transactional
    public ProfileVO changeBinding(BindingChangeReq req) {
        User user = currentUser();
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new BizException(ResultCode.VALID_ERROR, "当前密码不正确");
        }
        String value = req.getValue().trim();
        User patch = new User();
        patch.setId(user.getId());
        if ("PHONE".equals(req.getType())) {
            if (!value.matches("^1[3-9]\\d{9}$")) {
                throw new BizException(ResultCode.VALID_ERROR, "手机号格式不正确");
            }
            if (userService.existsPhone(value, user.getId())) {
                throw new BizException(ResultCode.VALID_ERROR, "该手机号已被其他账号使用");
            }
            patch.setPhone(value);
        } else {
            if (!value.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new BizException(ResultCode.VALID_ERROR, "邮箱格式不正确");
            }
            if (userService.existsEmail(value, user.getId())) {
                throw new BizException(ResultCode.VALID_ERROR, "该邮箱已被其他账号使用");
            }
            patch.setEmail(value);
        }
        userMapper.updateById(patch);
        return profile();
    }

    // ============================ 头像 ============================

    /**
     * 上传当前用户头像。
     *
     * <p>在通用文件校验之上<b>再叠加</b>头像专用校验（仅图片扩展名 + 2MB 上限）：
     * 通用配置允许 pdf/zip，但头像位只能是图片。</p>
     *
     * @param file 头像文件
     * @return 头像相对路径（同时写入 {@code user.avatar}）
     */
    @Transactional
    public String uploadAvatar(MultipartFile file) {
        User user = currentUser();
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "请选择头像文件");
        }
        if (file.getSize() > Constants.MAX_AVATAR_SIZE) {
            throw new BizException(ResultCode.FILE_TOO_LARGE, "头像不能超过 2MB");
        }
        String ext = FileUtil.extensionOf(file.getOriginalFilename());
        if (!Constants.AVATAR_EXTS.contains(ext)) {
            throw new BizException(ResultCode.VALID_ERROR,
                    "头像仅支持 " + String.join("/", Constants.AVATAR_EXTS) + " 格式");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BizException(ResultCode.VALID_ERROR, "头像必须是图片文件");
        }
        // 复用通用大小/扩展名校验（读运行期 file_config，改配置即刻生效）
        fileConfigService.validate(file);

        FileRecord record = fileRecordService.store(
                file, Constants.BIZ_TYPE_AVATAR, user.getId(), user.getId());
        User patch = new User();
        patch.setId(user.getId());
        patch.setAvatar(record.getRelativePath());
        userMapper.updateById(patch);
        return record.getRelativePath();
    }

    /**
     * 输出指定用户的头像字节流（登录即可，<b>不复用 file:list 下载权限</b>，ARCH §8.2）。
     *
     * @param userId   目标用户 id，为 null 时取当前登录用户
     * @param response HTTP 响应
     * @throws IOException 写流失败
     */
    public void writeAvatar(Long userId, HttpServletResponse response) throws IOException {
        Long targetId = userId == null ? currentUserId() : userId;
        User user = userMapper.selectById(targetId);
        if (user == null || user.getAvatar() == null || user.getAvatar().isBlank()) {
            throw new BizException(ResultCode.NOT_FOUND, "该用户未设置头像");
        }
        FileRecord record = fileRecordService.findByRelativePath(user.getAvatar());
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND, "头像文件记录不存在");
        }
        fileRecordService.writeStream(record, response, true);
    }

    // ============================ 活动记录 ============================

    /**
     * 个人活动记录：登录日志 + 本人问题操作历史，归并为统一时间线。
     *
     * <p>两路各取 {@code page*size} 条后在内存按时间倒序归并再切片（ARCH §8.4）。
     * 因此 {@code total} 在 {@code type=ALL} 且深翻页时是<b>取数窗口内的近似值</b>，
     * 前端超过 50 页应引导用户切换到单一类型查询（退化为精确单表分页）。</p>
     *
     * @param req 分页与筛选请求
     * @return 分页结果
     */
    public PageResult<ActivityVO> activities(ActivityPageReq req) {
        Long userId = currentUserId();
        int page = (req.getPage() == null || req.getPage() < 1) ? Constants.DEFAULT_PAGE : req.getPage();
        int size = (req.getSize() == null || req.getSize() < 1) ? Constants.DEFAULT_SIZE : req.getSize();
        String type = (req.getType() == null || req.getType().isBlank()) ? "ALL" : req.getType().toUpperCase();
        LocalDateTime start = parseStart(req.getStartDate());
        LocalDateTime end = parseEnd(req.getEndDate());
        int fetch = Math.min(page * size, ACTIVITY_FETCH_CAP);

        List<ActivityVO> merged = new ArrayList<>();
        if ("ALL".equals(type) || "LOGIN".equals(type)) {
            merged.addAll(loginActivities(userId, start, end, fetch));
        }
        if ("ALL".equals(type) || "ISSUE".equals(type)) {
            merged.addAll(issueActivities(userId, start, end, fetch));
        }
        merged.sort(Comparator.comparing(ActivityVO::getTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = merged.size();
        int from = Math.min((page - 1) * size, merged.size());
        int to = Math.min(from + size, merged.size());
        List<ActivityVO> slice = new ArrayList<>(merged.subList(from, to));
        return PageResult.of(slice, total, (long) page, (long) size);
    }

    /**
     * 登录日志 → 活动条目。
     */
    private List<ActivityVO> loginActivities(Long userId, LocalDateTime start,
                                             LocalDateTime end, int limit) {
        List<LoginLog> logs = loginLogService.recentOfUser(userId, start, end, limit);
        List<ActivityVO> list = new ArrayList<>(logs.size());
        for (LoginLog row : logs) {
            boolean success = row.getSuccess() != null && row.getSuccess() == 1;
            ActivityVO vo = new ActivityVO();
            vo.setType("LOGIN");
            vo.setTime(row.getLoginAt());
            vo.setTitle(success ? "登录成功" : "登录失败");
            vo.setDetail(success ? "" : (row.getFailReason() == null ? "" : row.getFailReason()));
            vo.setIp(row.getIp());
            vo.setDevice(buildDevice(row));
            vo.setSuccess(success);
            list.add(vo);
        }
        return list;
    }

    /**
     * 问题操作历史 → 活动条目。
     */
    private List<ActivityVO> issueActivities(Long userId, LocalDateTime start,
                                             LocalDateTime end, int limit) {
        List<Map<String, Object>> rows =
                issueHistoryMapper.selectMyActivities(userId, start, end, limit);
        List<ActivityVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ActivityVO vo = new ActivityVO();
            vo.setType("ISSUE");
            vo.setTime(toLocalDateTime(row.get("createdAt")));
            String action = asString(row.get("action"));
            HistoryActionEnum actionEnum = HistoryActionEnum.getByCode(action);
            vo.setTitle(actionEnum == null ? action : actionEnum.getDesc());
            String issueTitle = asString(row.get("issueTitle"));
            String remark = asString(row.get("remark"));
            vo.setDetail(remark == null || remark.isBlank() ? issueTitle : issueTitle + "：" + remark);
            vo.setIssueId(toLong(row.get("issueId")));
            vo.setIssueNo(asString(row.get("issueNo")));
            vo.setSuccess(Boolean.TRUE);
            list.add(vo);
        }
        return list;
    }

    // ============================ 私有工具 ============================

    /**
     * 取当前登录用户实体。
     *
     * @return 用户实体
     * @throws BizException 未登录或用户不存在
     */
    private User currentUser() {
        User user = userMapper.selectById(currentUserId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }

    /**
     * 取当前登录用户 id。
     *
     * @return 用户 id
     * @throws BizException 未登录
     */
    private Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 把当前请求携带的 token 拉入黑名单（改密后强制登出）。
     */
    private void blacklistCurrentToken() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return;
        }
        try {
            String token = header.substring(7);
            String jti = jwtUtil.getJti(token);
            Date expiration = jwtUtil.getExpiration(token);
            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(
                        Constants.REDIS_JWT_BLACKLIST_PREFIX + jti, "1", ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("[Profile] blacklist token after password change failed: {}", e.getMessage());
        }
    }

    /**
     * 取当前 HTTP 请求（非 Web 上下文返回 null）。
     *
     * @return 请求对象，可为 null
     */
    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private String buildDevice(LoginLog row) {
        String browser = (row.getBrowser() == null || row.getBrowser().isBlank())
                ? UserAgentParser.UNKNOWN : row.getBrowser();
        String os = (row.getOs() == null || row.getOs().isBlank())
                ? UserAgentParser.UNKNOWN : row.getOs();
        return browser + " / " + os;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDateTime parseStart(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.atStartOfDay();
    }

    private LocalDateTime parseEnd(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.atTime(LocalTime.MAX);
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Timestamp(date.getTime()).toLocalDateTime();
        }
        return null;
    }
}
