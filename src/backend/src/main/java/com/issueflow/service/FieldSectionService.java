package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.fieldconfig.req.FieldSectionReq;
import com.issueflow.dto.fieldconfig.resp.FieldSectionVO;
import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.FieldSection;
import com.issueflow.mapper.FieldConfigMapper;
import com.issueflow.mapper.FieldSectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段区域服务（ARCH §3.1，T03）。
 * <p>区域 CRUD + 启停；{@code is_system=1} 区域仅可改名/排序（删除硬拦截）。任何写操作后失效
 * {@link FieldSchemaCache}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldSectionService {

    private final FieldSectionMapper sectionMapper;
    private final FieldConfigMapper configMapper;
    private final FieldSchemaCache schemaCache;
    private final PermissionService permissionService;

    public List<FieldSectionVO> list() {
        permissionService.requirePermission("field:config:list");
        List<FieldSection> rows = sectionMapper.selectList(
                new LambdaQueryWrapper<FieldSection>().orderByAsc(FieldSection::getSort).orderByAsc(FieldSection::getId));
        return rows.stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
    }

    public FieldSectionVO detail(Long id) {
        permissionService.requirePermission("field:config:list");
        return toVO(requireExists(id));
    }

    @Transactional
    public Long create(FieldSectionReq req) {
        permissionService.requirePermission("field:config:save");
        assertCodeUnique(req.getCode(), null);
        FieldSection e = new FieldSection();
        e.setCode(req.getCode());
        e.setName(req.getName());
        e.setI18nKey(req.getI18nKey());
        e.setTypeScope(req.getTypeScope() == null || req.getTypeScope().isBlank() ? "GLOBAL" : req.getTypeScope());
        e.setSort(req.getSort() == null ? 0 : req.getSort());
        e.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        e.setIsSystem(0);
        sectionMapper.insert(e);
        schemaCache.evict(e.getTypeScope());
        return e.getId();
    }

    @Transactional
    public void update(Long id, FieldSectionReq req) {
        permissionService.requirePermission("field:config:save");
        FieldSection e = requireExists(id);
        boolean system = e.getIsSystem() != null && e.getIsSystem() == 1;
        // 系统区域：仅放行 name / i18nKey / sort（code / typeScope 不可改）
        if (system) {
            if (req.getName() != null) {
                e.setName(req.getName());
            }
            if (req.getI18nKey() != null) {
                e.setI18nKey(req.getI18nKey());
            }
            if (req.getSort() != null) {
                e.setSort(req.getSort());
            }
            if (req.getEnabled() != null) {
                e.setEnabled(req.getEnabled());
            }
            sectionMapper.updateById(e);
            schemaCache.evict(e.getTypeScope());
            return;
        }
        if (req.getName() != null) {
            e.setName(req.getName());
        }
        if (req.getI18nKey() != null) {
            e.setI18nKey(req.getI18nKey());
        }
        if (req.getSort() != null) {
            e.setSort(req.getSort());
        }
        if (req.getTypeScope() != null) {
            e.setTypeScope(req.getTypeScope());
        }
        if (req.getEnabled() != null) {
            e.setEnabled(req.getEnabled());
        }
        sectionMapper.updateById(e);
        schemaCache.evict(e.getTypeScope());
    }

    @Transactional
    public void delete(Long id) {
        permissionService.requirePermission("field:config:delete");
        FieldSection e = requireExists(id);
        if (e.getIsSystem() != null && e.getIsSystem() == 1) {
            throw new BizException(ResultCode.FIELD_SYSTEM_PROTECTED);
        }
        long fieldCount = configMapper.selectCount(
                new LambdaQueryWrapper<FieldConfig>().eq(FieldConfig::getSectionId, id));
        if (fieldCount > 0) {
            throw new BizException(ResultCode.VALID_ERROR, "该区域下仍有字段，无法删除");
        }
        sectionMapper.deleteById(id);
        schemaCache.evict(e.getTypeScope());
    }

    @Transactional
    public void toggleStatus(Long id, Boolean enabled) {
        permissionService.requirePermission("field:config:save");
        FieldSection e = requireExists(id);
        e.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        sectionMapper.updateById(e);
        schemaCache.evict(e.getTypeScope());
    }

    private FieldSection requireExists(Long id) {
        FieldSection e = sectionMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字段区域不存在");
        }
        return e;
    }

    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<FieldSection> wrapper = new LambdaQueryWrapper<FieldSection>().eq(FieldSection::getCode, code);
        if (excludeId != null) {
            wrapper.ne(FieldSection::getId, excludeId);
        }
        if (sectionMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.FIELD_CODE_DUPLICATE);
        }
    }

    private FieldSectionVO toVO(FieldSection s) {
        FieldSectionVO vo = new FieldSectionVO();
        vo.setId(s.getId());
        vo.setCode(s.getCode());
        vo.setName(s.getName());
        vo.setI18nKey(s.getI18nKey());
        vo.setTypeScope(s.getTypeScope());
        vo.setSort(s.getSort());
        vo.setEnabled(toBool(s.getEnabled()));
        vo.setIsSystem(toBool(s.getIsSystem()));
        return vo;
    }

    private Boolean toBool(Integer v) {
        return v != null && v == 1;
    }
}
