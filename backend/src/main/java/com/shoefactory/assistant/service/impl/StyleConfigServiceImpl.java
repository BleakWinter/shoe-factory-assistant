package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.StyleConfigResponse;
import com.shoefactory.assistant.dto.StyleConfigSaveRequest;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShoeStyleConfigMapper;
import com.shoefactory.assistant.service.StyleConfigService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class StyleConfigServiceImpl implements StyleConfigService {

    private final ShoeStyleConfigMapper shoeStyleConfigMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;

    public StyleConfigServiceImpl(
            ShoeStyleConfigMapper shoeStyleConfigMapper,
            OrderRecordMapper orderRecordMapper,
            OrderRecordDetailMapper orderRecordDetailMapper
    ) {
        this.shoeStyleConfigMapper = shoeStyleConfigMapper;
        this.orderRecordMapper = orderRecordMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
    }

    @Override
    public PageResponse<StyleConfigResponse> listStyleConfigs(
            String developmentNo,
            Boolean incompleteOnly,
            long page,
            long size
    ) {
        Page<ShoeStyleConfig> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<ShoeStyleConfig> wrapper = new LambdaQueryWrapper<ShoeStyleConfig>()
                .like(hasText(developmentNo), ShoeStyleConfig::getDevelopmentNo, developmentNo)
                .orderByAsc(ShoeStyleConfig::getDevelopmentNo);
        if (Boolean.TRUE.equals(incompleteOnly)) {
            wrapper.and(nested -> nested
                    .isNull(ShoeStyleConfig::getBoxSpec)
                    .or()
                    .eq(ShoeStyleConfig::getBoxSpec, "")
                    .or()
                    .isNull(ShoeStyleConfig::getNetWeightPerPair)
                    .or()
                    .isNull(ShoeStyleConfig::getGrossWeightPerPair));
        }
        Page<ShoeStyleConfig> resultPage = shoeStyleConfigMapper.selectPage(pageRequest, wrapper);
        List<StyleConfigResponse> records = resultPage.getRecords().stream()
                .map(StyleConfigResponse::from)
                .toList();
        return PageResponse.from(resultPage, records);
    }

    @Override
    @Transactional
    public StyleConfigResponse createStyleConfig(StyleConfigSaveRequest request) {
        String developmentNo = normalizeDevelopmentNo(request.getDevelopmentNo());
        if (!hasText(developmentNo)) {
            throw new BusinessException("开发编号不能为空");
        }
        if (exists(developmentNo)) {
            throw new BusinessException("开发编号已存在: " + developmentNo);
        }

        LocalDateTime now = LocalDateTime.now();
        ShoeStyleConfig config = new ShoeStyleConfig();
        config.setDevelopmentNo(developmentNo);
        applyEditableFields(config, request);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        try {
            shoeStyleConfigMapper.insert(config);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("开发编号已存在: " + developmentNo, ex);
        }
        return StyleConfigResponse.from(config);
    }

    @Override
    @Transactional
    public StyleConfigResponse updateStyleConfig(Long id, StyleConfigSaveRequest request) {
        ShoeStyleConfig config = shoeStyleConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("开发编号配置不存在: " + id);
        }
        String boxSpec = blankToNull(request.getBoxSpec());
        LocalDateTime now = LocalDateTime.now();
        shoeStyleConfigMapper.update(null, new LambdaUpdateWrapper<ShoeStyleConfig>()
                .eq(ShoeStyleConfig::getId, id)
                .set(ShoeStyleConfig::getBoxSpec, boxSpec)
                .set(ShoeStyleConfig::getNetWeightPerPair, request.getNetWeightPerPair())
                .set(ShoeStyleConfig::getGrossWeightPerPair, request.getGrossWeightPerPair())
                .set(ShoeStyleConfig::getUpdatedAt, now));
        config.setBoxSpec(boxSpec);
        config.setNetWeightPerPair(request.getNetWeightPerPair());
        config.setGrossWeightPerPair(request.getGrossWeightPerPair());
        config.setUpdatedAt(now);
        return StyleConfigResponse.from(config);
    }

    @Override
    public List<String> listUnconfiguredDevelopmentNos() {
        Set<String> discoveredDevelopmentNos = collectKnownDevelopmentNos();
        if (discoveredDevelopmentNos.isEmpty()) {
            return List.of();
        }
        Set<String> configuredDevelopmentNos = shoeStyleConfigMapper.selectList(new LambdaQueryWrapper<ShoeStyleConfig>()
                        .select(ShoeStyleConfig::getDevelopmentNo)
                        .in(ShoeStyleConfig::getDevelopmentNo, discoveredDevelopmentNos))
                .stream()
                .map(ShoeStyleConfig::getDevelopmentNo)
                .map(this::normalizeDevelopmentNo)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        return discoveredDevelopmentNos.stream()
                .filter(developmentNo -> !configuredDevelopmentNos.contains(developmentNo))
                .toList();
    }

    @Override
    @Transactional
    public void ensureConfigsForDevelopmentNos(Collection<String> developmentNos) {
        List<String> normalizedDevelopmentNos = normalizeDevelopmentNos(developmentNos);
        if (normalizedDevelopmentNos.isEmpty()) {
            return;
        }
        Set<String> existingDevelopmentNos = shoeStyleConfigMapper.selectList(new LambdaQueryWrapper<ShoeStyleConfig>()
                        .select(ShoeStyleConfig::getDevelopmentNo)
                        .in(ShoeStyleConfig::getDevelopmentNo, normalizedDevelopmentNos))
                .stream()
                .map(ShoeStyleConfig::getDevelopmentNo)
                .map(this::normalizeDevelopmentNo)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (String developmentNo : normalizedDevelopmentNos) {
            if (existingDevelopmentNos.contains(developmentNo)) {
                continue;
            }
            ShoeStyleConfig config = new ShoeStyleConfig();
            config.setDevelopmentNo(developmentNo);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            try {
                shoeStyleConfigMapper.insert(config);
            } catch (DuplicateKeyException ignored) {
                // Another request may have created the same discovered style first.
            }
        }
    }

    private Set<String> collectKnownDevelopmentNos() {
        Set<String> developmentNos = new TreeSet<>();
        orderRecordMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                        .select(OrderRecord::getDevelopmentNos)
                        .isNotNull(OrderRecord::getDevelopmentNos)
                        .ne(OrderRecord::getDevelopmentNos, ""))
                .forEach(order -> developmentNos.addAll(splitDevelopmentNos(order.getDevelopmentNos())));
        orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                        .select(OrderRecordDetail::getDevelopmentNo)
                        .isNotNull(OrderRecordDetail::getDevelopmentNo)
                        .ne(OrderRecordDetail::getDevelopmentNo, ""))
                .forEach(detail -> {
                    String developmentNo = normalizeDevelopmentNo(detail.getDevelopmentNo());
                    if (hasText(developmentNo)) {
                        developmentNos.add(developmentNo);
                    }
                });
        return developmentNos;
    }

    private void applyEditableFields(ShoeStyleConfig config, StyleConfigSaveRequest request) {
        config.setBoxSpec(blankToNull(request.getBoxSpec()));
        config.setNetWeightPerPair(request.getNetWeightPerPair());
        config.setGrossWeightPerPair(request.getGrossWeightPerPair());
    }

    private boolean exists(String developmentNo) {
        return shoeStyleConfigMapper.selectCount(new LambdaQueryWrapper<ShoeStyleConfig>()
                .eq(ShoeStyleConfig::getDevelopmentNo, developmentNo)) > 0;
    }

    private List<String> splitDevelopmentNos(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(this::normalizeDevelopmentNo)
                .filter(this::hasText)
                .toList();
    }

    private List<String> normalizeDevelopmentNos(Collection<String> developmentNos) {
        if (developmentNos == null || developmentNos.isEmpty()) {
            return List.of();
        }
        return developmentNos.stream()
                .map(this::normalizeDevelopmentNo)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String normalizeDevelopmentNo(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
