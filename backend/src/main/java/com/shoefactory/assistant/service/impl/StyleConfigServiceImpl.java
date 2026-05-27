package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShoePriceConfigResponse;
import com.shoefactory.assistant.dto.ShoePriceConfigSaveRequest;
import com.shoefactory.assistant.dto.StyleConfigResponse;
import com.shoefactory.assistant.dto.StyleConfigSaveRequest;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShoeStyleConfigMapper;
import com.shoefactory.assistant.service.StyleConfigService;
import com.shoefactory.assistant.util.DevelopmentNoUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
            String developmentNos,
            Boolean incompleteOnly,
            long page,
            long size
    ) {
        List<String> parsedDevelopmentNos = splitDevelopmentNos(developmentNos);
        Page<ShoeStyleConfig> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<ShoeStyleConfig> wrapper = new LambdaQueryWrapper<ShoeStyleConfig>()
                .orderByAsc(ShoeStyleConfig::getDevelopmentNo);
        applyDevelopmentNoFilter(wrapper, parsedDevelopmentNos);
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

    private void applyDevelopmentNoFilter(
            LambdaQueryWrapper<ShoeStyleConfig> wrapper,
            List<String> developmentNos
    ) {
        if (developmentNos.isEmpty()) {
            return;
        }
        wrapper.and(nested -> {
            for (int index = 0; index < developmentNos.size(); index++) {
                if (index > 0) {
                    nested.or();
                }
                nested.like(ShoeStyleConfig::getDevelopmentNo, developmentNos.get(index));
            }
        });
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
    public PageResponse<ShoePriceConfigResponse> listShoePriceConfigs(
            String developmentNos,
            Boolean incompleteOnly,
            long page,
            long size
    ) {
        List<String> parsedDevelopmentNos = splitDevelopmentNos(developmentNos);
        Page<ShoeStyleConfig> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<ShoeStyleConfig> wrapper = new LambdaQueryWrapper<ShoeStyleConfig>()
                .orderByAsc(ShoeStyleConfig::getDevelopmentNo);
        applyDevelopmentNoFilter(wrapper, parsedDevelopmentNos);
        if (Boolean.TRUE.equals(incompleteOnly)) {
            wrapper.isNull(ShoeStyleConfig::getShoePrice);
        }
        Page<ShoeStyleConfig> resultPage = shoeStyleConfigMapper.selectPage(pageRequest, wrapper);
        List<ShoePriceConfigResponse> records = resultPage.getRecords().stream()
                .map(ShoePriceConfigResponse::from)
                .toList();
        attachUpperMaterials(records);
        return PageResponse.from(resultPage, records);
    }

    @Override
    @Transactional
    public ShoePriceConfigResponse createShoePriceConfig(ShoePriceConfigSaveRequest request) {
        String developmentNo = normalizeDevelopmentNo(request.getDevelopmentNo());
        if (!hasText(developmentNo)) {
            throw new BusinessException("开发编号不能为空");
        }
        ShoeStyleConfig existingConfig = shoeStyleConfigMapper.selectOne(new LambdaQueryWrapper<ShoeStyleConfig>()
                .eq(ShoeStyleConfig::getDevelopmentNo, developmentNo));
        if (existingConfig != null) {
            return applyShoePrice(existingConfig, request.getShoePrice());
        }

        LocalDateTime now = LocalDateTime.now();
        ShoeStyleConfig config = new ShoeStyleConfig();
        config.setDevelopmentNo(developmentNo);
        config.setShoePrice(request.getShoePrice());
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        try {
            shoeStyleConfigMapper.insert(config);
        } catch (DuplicateKeyException ex) {
            ShoeStyleConfig configCreatedByOtherRequest = shoeStyleConfigMapper.selectOne(
                    new LambdaQueryWrapper<ShoeStyleConfig>()
                            .eq(ShoeStyleConfig::getDevelopmentNo, developmentNo));
            if (configCreatedByOtherRequest == null) {
                throw new BusinessException("开发编号已存在: " + developmentNo, ex);
            }
            return applyShoePrice(configCreatedByOtherRequest, request.getShoePrice());
        }
        return ShoePriceConfigResponse.from(config);
    }

    @Override
    @Transactional
    public ShoePriceConfigResponse updateShoePriceConfig(Long id, ShoePriceConfigSaveRequest request) {
        ShoeStyleConfig config = shoeStyleConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("开发编号配置不存在: " + id);
        }
        return applyShoePrice(config, request.getShoePrice());
    }

    @Override
    public List<String> listUnpricedDevelopmentNos() {
        Set<String> discoveredDevelopmentNos = collectKnownDevelopmentNos();
        if (discoveredDevelopmentNos.isEmpty()) {
            return List.of();
        }
        Set<String> pricedDevelopmentNos = shoeStyleConfigMapper.selectList(new LambdaQueryWrapper<ShoeStyleConfig>()
                        .select(ShoeStyleConfig::getDevelopmentNo, ShoeStyleConfig::getShoePrice)
                        .in(ShoeStyleConfig::getDevelopmentNo, discoveredDevelopmentNos)
                        .isNotNull(ShoeStyleConfig::getShoePrice))
                .stream()
                .map(ShoeStyleConfig::getDevelopmentNo)
                .map(this::normalizeDevelopmentNo)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        return discoveredDevelopmentNos.stream()
                .filter(developmentNo -> !pricedDevelopmentNos.contains(developmentNo))
                .toList();
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
    public List<DevelopmentNoOptionResponse> listDevelopmentNoOptions() {
        List<DevelopmentNoOptionResponse> options = new ArrayList<>();
        shoeStyleConfigMapper.selectList(new LambdaQueryWrapper<ShoeStyleConfig>()
                        .select(ShoeStyleConfig::getDevelopmentNo)
                        .isNotNull(ShoeStyleConfig::getDevelopmentNo)
                        .ne(ShoeStyleConfig::getDevelopmentNo, ""))
                .stream()
                .map(ShoeStyleConfig::getDevelopmentNo)
                .map(this::normalizeDevelopmentNo)
                .filter(this::hasText)
                .distinct()
                .forEach(developmentNo ->
                        appendDevelopmentNoOption(options, parseDevelopmentNoParts(developmentNo), List.of()));
        sortDevelopmentNoOptions(options);
        return options;
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

    private List<String> parseDevelopmentNoParts(String value) {
        List<String> parts = Arrays.stream(value.trim().split("-"))
                .map(String::trim)
                .filter(this::hasText)
                .toList();
        if (parts.size() >= 3) {
            return parts.subList(parts.size() - 3, parts.size());
        }
        return parts;
    }

    private void appendDevelopmentNoOption(
            List<DevelopmentNoOptionResponse> nodes,
            List<String> parts,
            List<String> path
    ) {
        if (parts.isEmpty()) {
            return;
        }
        String part = parts.get(0);
        List<String> nextPath = new ArrayList<>(path);
        nextPath.add(part);
        DevelopmentNoOptionResponse node = findDevelopmentNoNode(nodes, part);
        if (node == null) {
            node = new DevelopmentNoOptionResponse(String.join("-", nextPath), part,
                    parts.size() > 1 ? new ArrayList<>() : null);
            nodes.add(node);
        }
        if (parts.size() > 1) {
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            appendDevelopmentNoOption(node.getChildren(), parts.subList(1, parts.size()), nextPath);
        }
    }

    private DevelopmentNoOptionResponse findDevelopmentNoNode(
            List<DevelopmentNoOptionResponse> nodes,
            String label
    ) {
        return nodes.stream()
                .filter(node -> label.equals(node.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private void sortDevelopmentNoOptions(List<DevelopmentNoOptionResponse> options) {
        options.sort((left, right) -> compareDevelopmentNoLabel(left.getLabel(), right.getLabel()));
        options.stream()
                .filter(option -> option.getChildren() != null)
                .forEach(option -> sortDevelopmentNoOptions(option.getChildren()));
    }

    private int compareDevelopmentNoLabel(String left, String right) {
        Integer leftNumber = parseInteger(left);
        Integer rightNumber = parseInteger(right);
        if (leftNumber != null && rightNumber != null && !leftNumber.equals(rightNumber)) {
            return leftNumber.compareTo(rightNumber);
        }
        return left.compareToIgnoreCase(right);
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void applyEditableFields(ShoeStyleConfig config, StyleConfigSaveRequest request) {
        config.setBoxSpec(blankToNull(request.getBoxSpec()));
        config.setNetWeightPerPair(request.getNetWeightPerPair());
        config.setGrossWeightPerPair(request.getGrossWeightPerPair());
    }

    private ShoePriceConfigResponse applyShoePrice(ShoeStyleConfig config, BigDecimal shoePrice) {
        LocalDateTime now = LocalDateTime.now();
        shoeStyleConfigMapper.update(null, new LambdaUpdateWrapper<ShoeStyleConfig>()
                .eq(ShoeStyleConfig::getId, config.getId())
                .set(ShoeStyleConfig::getShoePrice, shoePrice)
                .set(ShoeStyleConfig::getUpdatedAt, now));
        config.setShoePrice(shoePrice);
        config.setUpdatedAt(now);
        return ShoePriceConfigResponse.from(config);
    }

    private void attachUpperMaterials(List<ShoePriceConfigResponse> records) {
        List<String> developmentNos = records.stream()
                .map(ShoePriceConfigResponse::getDevelopmentNo)
                .map(this::normalizeDevelopmentNo)
                .filter(this::hasText)
                .distinct()
                .toList();
        Map<String, String> upperMaterialsByDevelopmentNo = collectUpperMaterialsByDevelopmentNo(developmentNos);
        records.forEach(record -> record.setUpperMaterial(
                upperMaterialsByDevelopmentNo.get(normalizeDevelopmentNo(record.getDevelopmentNo()))));
    }

    private Map<String, String> collectUpperMaterialsByDevelopmentNo(Collection<String> developmentNos) {
        if (developmentNos == null || developmentNos.isEmpty()) {
            return Map.of();
        }
        Map<String, LinkedHashSet<String>> materialSets = new LinkedHashMap<>();
        orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                        .select(OrderRecordDetail::getDevelopmentNo, OrderRecordDetail::getUpperMaterial)
                        .in(OrderRecordDetail::getDevelopmentNo, developmentNos)
                        .isNotNull(OrderRecordDetail::getUpperMaterial)
                        .ne(OrderRecordDetail::getUpperMaterial, ""))
                .forEach(detail -> {
                    String developmentNo = normalizeDevelopmentNo(detail.getDevelopmentNo());
                    String upperMaterial = blankToNull(detail.getUpperMaterial());
                    if (hasText(developmentNo) && hasText(upperMaterial)) {
                        materialSets.computeIfAbsent(developmentNo, key -> new LinkedHashSet<>()).add(upperMaterial);
                    }
                });
        return materialSets.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(" / ", entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
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
                .map(DevelopmentNoUtil::normalizeSearchTerm)
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
        return DevelopmentNoUtil.normalize(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
