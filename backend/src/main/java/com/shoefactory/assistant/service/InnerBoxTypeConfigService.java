package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.InnerBoxTypeSaveRequest;
import com.shoefactory.assistant.entity.InnerBoxTypeConfig;
import com.shoefactory.assistant.mapper.InnerBoxTypeConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InnerBoxTypeConfigService {
    private final InnerBoxTypeConfigMapper mapper;

    public InnerBoxTypeConfigService(InnerBoxTypeConfigMapper mapper) { this.mapper = mapper; }

    public List<InnerBoxTypeConfig> list(Boolean enabled) {
        return mapper.selectList(new LambdaQueryWrapper<InnerBoxTypeConfig>()
                .eq(enabled != null, InnerBoxTypeConfig::getEnabled, enabled)
                .orderByAsc(InnerBoxTypeConfig::getName).orderByAsc(InnerBoxTypeConfig::getId));
    }

    public Map<Long, InnerBoxTypeConfig> byIds(Collection<Long> ids) {
        List<Long> values = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (values.isEmpty()) return Map.of();
        return mapper.selectBatchIds(values).stream()
                .collect(Collectors.toMap(InnerBoxTypeConfig::getId, Function.identity()));
    }

    public InnerBoxTypeConfig requireEnabled(Long id) {
        if (id == null) throw new BusinessException("请选择内盒类型");
        InnerBoxTypeConfig type = require(id);
        if (!Boolean.TRUE.equals(type.getEnabled())) throw new BusinessException("内盒类型已停用，请重新选择");
        return type;
    }

    private InnerBoxTypeConfig require(Long id) {
        InnerBoxTypeConfig type = mapper.selectById(id);
        if (type == null) throw new BusinessException("内盒类型不存在，请重新选择");
        return type;
    }

    @Transactional
    public InnerBoxTypeConfig save(Long id, InnerBoxTypeSaveRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) throw new BusinessException("请输入内盒类型名称");
        InnerBoxTypeConfig type = id == null ? new InnerBoxTypeConfig() : require(id);
        if (mapper.selectCount(new LambdaQueryWrapper<InnerBoxTypeConfig>()
                .eq(InnerBoxTypeConfig::getName, name).ne(id != null, InnerBoxTypeConfig::getId, id)) > 0) {
            throw new BusinessException("内盒类型名称已存在");
        }
        type.setName(name);
        type.setRemark(request.remark() == null ? null : request.remark().trim());
        type.setEnabled(request.enabled());
        type.setUpdatedAt(LocalDateTime.now());
        try {
            if (id == null) {
                type.setCreatedAt(type.getUpdatedAt());
                mapper.insert(type);
            } else {
                // Explicitly update nullable remarks so clearing the input also clears the database value.
                mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InnerBoxTypeConfig>()
                        .eq(InnerBoxTypeConfig::getId, id)
                        .set(InnerBoxTypeConfig::getName, name)
                        .set(InnerBoxTypeConfig::getRemark, type.getRemark())
                        .set(InnerBoxTypeConfig::getEnabled, type.getEnabled())
                        .set(InnerBoxTypeConfig::getUpdatedAt, type.getUpdatedAt()));
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("内盒类型名称已存在", ex);
        }
        return type;
    }
}
