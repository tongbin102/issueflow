package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.IssueFieldValue;
import com.issueflow.enums.FieldType;
import com.issueflow.mapper.FieldConfigMapper;
import com.issueflow.mapper.IssueFieldValueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问题自定义字段值服务（竖表，ARCH §2.3 / T03 子项 9-10 支撑）。
 * <p>仅承载 {@code is_system=0} 的自定义字段值（内置字段值仍在 issue 主表原列）。
 * 按 field_config.type 选择落库列（value_text / value_num / value_date），(issue_id, field_code)
 * 条件唯一，保存即一次 upsert（先软删旧值再插入）。</p>
 *
 * <p>注：IssueService 在 create/update/detail 中调用本服务完成字段值装配/拆解，该接线属于
 * IssueForm 消费阶段（T05），本服务保持与 IssueService 解耦、可独立测试。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueFieldValueService {

    private final IssueFieldValueMapper valueMapper;
    private final FieldConfigMapper configMapper;

    /**
     * 保存某问题的全部自定义字段值（upsert）。仅处理自定义（is_system=0）且 type 已知的字段。
     *
     * @param issueId 问题 id
     * @param values  fieldCode -> 值（TEXT/DICT/REF 为字符串或逗号拼接；NUMBER 为数字；DATE/DATETIME 为字符串）
     */
    @Transactional
    public void saveValues(Long issueId, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, FieldConfig> configByCode = loadCustomConfigs();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String code = entry.getKey();
            Object raw = entry.getValue();
            FieldConfig cfg = configByCode.get(code);
            if (cfg == null) {
                // 非自定义字段或不存在：跳过（内置字段由 IssueService 写主表）
                continue;
            }
            // Q5：停用字段只读——不接受新值、保留旧值、静默忽略（不报错）
            if (cfg.getEnabled() != null && cfg.getEnabled() == 0) {
                continue;
            }
            if (raw == null || (raw instanceof String && ((String) raw).isBlank())) {
                // 空值：软删该字段旧值，不插入
                softDelete(issueId, code);
                continue;
            }
            IssueFieldValue val = buildValue(issueId, cfg, raw);
            softDelete(issueId, code);
            valueMapper.insert(val);
        }
    }

    /**
     * 按 issue 取全部自定义字段值（已按 field_code 排序）。
     */
    public List<IssueFieldValue> listByIssue(Long issueId) {
        return valueMapper.selectList(new LambdaQueryWrapper<IssueFieldValue>()
                .eq(IssueFieldValue::getIssueId, issueId)
                .orderByAsc(IssueFieldValue::getFieldCode));
    }

    /**
     * 取某 issue 的自定义字段值映射（fieldCode -> 原始 IssueFieldValue）。
     */
    public Map<String, IssueFieldValue> mapByIssue(Long issueId) {
        Map<String, IssueFieldValue> map = new LinkedHashMap<>();
        for (IssueFieldValue v : listByIssue(issueId)) {
            map.put(v.getFieldCode(), v);
        }
        return map;
    }

    /**
     * 当前全部自定义字段配置（code → 配置，含停用项）。
     * <p>供 {@link com.issueflow.service.IssueService} 详情回填时按 {@code field_config.type}
     * 从 {@code issue_field_value} 各列取出真实值；含停用项以便回显其历史值。</p>
     *
     * @return field_code → FieldConfig
     */
    public Map<String, FieldConfig> customConfigs() {
        return loadCustomConfigs();
    }

    private void softDelete(Long issueId, String code) {
        List<IssueFieldValue> existing = valueMapper.selectList(new LambdaQueryWrapper<IssueFieldValue>()
                .eq(IssueFieldValue::getIssueId, issueId).eq(IssueFieldValue::getFieldCode, code));
        for (IssueFieldValue e : existing) {
            valueMapper.deleteById(e.getId());
        }
    }

    private IssueFieldValue buildValue(Long issueId, FieldConfig cfg, Object raw) {
        IssueFieldValue v = new IssueFieldValue();
        v.setIssueId(issueId);
        v.setFieldCode(cfg.getCode());
        FieldType type = FieldType.fromCode(cfg.getType());
        switch (type) {
            case NUMBER:
                v.setValueNum(toBigDecimal(raw));
                v.setValueText(raw.toString());
                break;
            case DATE:
                v.setValueDate(toDate(raw.toString()));
                break;
            case DATETIME:
                v.setValueDate(toDateTime(raw.toString()));
                break;
            case TEXT:
            case DICT:
            case REF:
            default:
                v.setValueText(raw.toString());
                break;
        }
        return v;
    }

    private Map<String, FieldConfig> loadCustomConfigs() {
        Map<String, FieldConfig> map = new LinkedHashMap<>();
        List<FieldConfig> all = configMapper.selectList(new LambdaQueryWrapper<FieldConfig>()
                .eq(FieldConfig::getIsSystem, 0));
        for (FieldConfig c : all) {
            map.put(c.getCode(), c);
        }
        return map;
    }

    private BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
        }
        if (raw instanceof Number) {
            return BigDecimal.valueOf(((Number) raw).doubleValue());
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.VALID_ERROR, "字段值非数值: " + raw);
        }
    }

    private LocalDateTime toDate(String s) {
        try {
            return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(s.trim().replace(' ', 'T'),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate().atStartOfDay();
            } catch (DateTimeParseException e2) {
                throw new BizException(ResultCode.VALID_ERROR, "日期格式应为 yyyy-MM-dd: " + s);
            }
        }
    }

    private LocalDateTime toDateTime(String s) {
        try {
            return LocalDateTime.parse(s.trim().replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            } catch (DateTimeParseException e2) {
                throw new BizException(ResultCode.VALID_ERROR, "日期时间格式应为 yyyy-MM-dd HH:mm:ss: " + s);
            }
        }
    }
}
