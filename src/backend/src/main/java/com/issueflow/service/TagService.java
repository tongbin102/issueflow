package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.entity.Tag;
import com.issueflow.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签字典服务
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    /**
     * 标签列表（按名称升序）
     */
    public List<Tag> list() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getName));
    }

    /**
     * 新增标签（名称唯一）
     */
    public Tag create(Tag tag) {
        if (tag.getName() == null || tag.getName().isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "标签名称不能为空");
        }
        if (tagMapper.selectCount(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tag.getName())) > 0) {
            throw new BizException(ResultCode.VALID_ERROR, "标签已存在");
        }
        tagMapper.insert(tag);
        return tag;
    }

    /**
     * 更新标签（仅更新非空字段）
     */
    public Tag update(Tag tag) {
        if (tag.getId() == null) {
            throw new BizException(ResultCode.VALID_ERROR, "标签 id 不能为空");
        }
        Tag exist = tagMapper.selectById(tag.getId());
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "标签不存在");
        }
        if (tag.getName() != null) {
            exist.setName(tag.getName());
        }
        if (tag.getColor() != null) {
            exist.setColor(tag.getColor());
        }
        tagMapper.updateById(exist);
        return exist;
    }

    /**
     * 逻辑删除标签
     */
    public void delete(Long id) {
        tagMapper.deleteById(id);
    }
}
