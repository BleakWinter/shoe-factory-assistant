package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PackingOrderItemRequest;
import com.shoefactory.assistant.dto.PackingOrderItemResponse;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShoeStyleConfigMapper;
import com.shoefactory.assistant.util.DevelopmentNoUtil;
import com.shoefactory.assistant.service.InnerBoxTypeConfigService;
import com.shoefactory.assistant.util.PackingDetailMatchUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PackingOrderSummaryService {
    private static final ObjectMapper JSON = new ObjectMapper().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private static final TypeReference<Map<String, Integer>> SIZES = new TypeReference<>() {};
    private final OrderRecordDetailMapper detailMapper;
    private final OrderPackingDetailMapper packingMapper;
    private final OrderRecordMapper orderMapper;
    private final ShoeStyleConfigMapper styleMapper;
    private final InnerBoxTypeConfigService boxTypes;

    public PackingOrderSummaryService(OrderRecordDetailMapper detailMapper, OrderPackingDetailMapper packingMapper,
                                      OrderRecordMapper orderMapper, ShoeStyleConfigMapper styleMapper, InnerBoxTypeConfigService boxTypes) {
        this.detailMapper = detailMapper;
        this.packingMapper = packingMapper;
        this.orderMapper = orderMapper;
        this.styleMapper = styleMapper;
        this.boxTypes = boxTypes;
    }

    public record PackingItem(PackingOrderItemResponse detail, String imagePath) {}

    public List<PackingItem> prepare(List<PackingOrderItemRequest> requests) {
        if (requests == null || requests.isEmpty()) throw new BusinessException("请选择订包装明细");
        Map<Long, PackingOrderItemRequest> requestById = new LinkedHashMap<>();
        for (PackingOrderItemRequest request : requests) {
            if (request == null || request.getSourceDetailIds() == null || request.getSourceDetailIds().isEmpty()) {
                throw new BusinessException("订包装明细不能为空");
            }
            for (Long id : request.getSourceDetailIds()) {
                if (id == null || id <= 0) throw new BusinessException("订包装来源明细无效");
                if (requestById.putIfAbsent(id, request) != null) throw new BusinessException("订包装来源明细不能重复");
            }
        }
        Map<Long, OrderRecordDetail> details = detailMapper.selectBatchIds(requestById.keySet()).stream()
                .collect(Collectors.toMap(OrderRecordDetail::getId, Function.identity()));
        Map<String, List<OrderRecordDetail>> groups = new LinkedHashMap<>();
        for (Long id : requestById.keySet()) {
            OrderRecordDetail source = details.get(id);
            if (source == null) throw new BusinessException("订单明细不存在，请刷新后重新选择: " + id);
            groups.computeIfAbsent(groupKey(source), key -> new ArrayList<>()).add(source);
        }
        for (PackingOrderItemRequest request : requests) {
            long groupCount = request.getSourceDetailIds().stream().map(details::get).map(PackingOrderSummaryService::groupKey).distinct().count();
            if (groupCount != 1) throw new BusinessException("一条订包装明细只能属于同一个订单和款号");
        }
        List<Long> orderIds = groups.values().stream().flatMap(List::stream).map(OrderRecordDetail::getOrderId).distinct().toList();
        // 选择的是订单中的整款；刷新和保存时补齐后来追加的同款来源行。
        for (OrderRecordDetail source : detailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                .in(OrderRecordDetail::getOrderId, orderIds).orderByAsc(OrderRecordDetail::getId))) {
            String style = styleKey(source.getDevelopmentNo());
            String key = source.getOrderId() + ":" + style;
            if (style == null || !groups.containsKey(key) || requestById.containsKey(source.getId())) continue;
            List<OrderRecordDetail> group = groups.get(key);
            requestById.put(source.getId(), requestById.get(group.get(0).getId()));
            details.put(source.getId(), source);
            group.add(source);
        }
        Map<Long, OrderRecord> orders = orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(OrderRecord::getId, Function.identity()));
        List<OrderPackingDetail> packing = packingMapper.selectList(new LambdaQueryWrapper<OrderPackingDetail>()
                .in(OrderPackingDetail::getOrderId, orderIds).orderByAsc(OrderPackingDetail::getId));
        List<String> styles = groups.values().stream().flatMap(List::stream).map(item -> styleKey(item.getDevelopmentNo())).distinct().toList();
        Map<String, String> boxSpecs = new HashMap<>();
        Map<String, Long> typeIdsByStyle = new HashMap<>();
        for (ShoeStyleConfig config : styleMapper.selectList(new LambdaQueryWrapper<ShoeStyleConfig>()
                .in(ShoeStyleConfig::getDevelopmentNo, styles))) {
            boxSpecs.put(styleKey(config.getDevelopmentNo()), config.getBoxSpec());
            typeIdsByStyle.put(styleKey(config.getDevelopmentNo()), config.getInnerBoxTypeId());
        }
        List<Long> allTypeIds = new ArrayList<>(typeIdsByStyle.values());
        requests.forEach(request -> allTypeIds.add(request.getInnerBoxTypeId()));
        var typeConfigs = boxTypes.byIds(allTypeIds);
        Map<Long, PackingOrderItemResponse> packingOwners = new HashMap<>();
        List<PackingItem> result = new ArrayList<>();
        for (Map.Entry<String, List<OrderRecordDetail>> group : groups.entrySet()) {
            List<OrderRecordDetail> sources = group.getValue();
            OrderRecordDetail first = sources.get(0);
            OrderRecord order = orders.get(first.getOrderId());
            if (order == null) throw new BusinessException("来源订单不存在，请刷新后重新选择");
            List<PackingOrderItemRequest> overrides = sources.stream().map(source -> requestById.get(source.getId())).distinct().toList();
            List<OrderPackingDetail> matched = packing.stream()
                    .filter(row -> PackingDetailMatchUtil.isMatchingPackingDetails(sources, row)).toList();
            PackingOrderItemResponse item = new PackingOrderItemResponse();
            item.setKey(group.getKey());
            item.setOrderId(first.getOrderId());
            item.setOrderNo(text(order.getOrderNo()) == null ? text(first.getCustomerOrderNo()) : text(order.getOrderNo()));
            item.setDevelopmentNo(styleKey(first.getDevelopmentNo()));
            item.setSourceDetailIds(sources.stream().map(OrderRecordDetail::getId).sorted().toList());
            item.setBoxSpec(override(overrides, PackingOrderItemRequest::getBoxSpec, boxSpecs.get(item.getDevelopmentNo())));
            List<Long> selectedTypes = overrides.stream().map(PackingOrderItemRequest::getInnerBoxTypeId)
                    .filter(Objects::nonNull).distinct().toList();
            if (selectedTypes.size() > 1) throw new BusinessException("同订单同款的内盒类型不一致，请合并后重新选择");
            Long typeId = selectedTypes.isEmpty() ? typeIdsByStyle.get(item.getDevelopmentNo()) : selectedTypes.get(0);
            item.setInnerBoxTypeId(typeId);
            var type = typeId == null ? null : typeConfigs.get(typeId);
            item.setInnerBoxTypeName(type == null ? null : type.getName());
            item.setRemark(override(overrides, PackingOrderItemRequest::getRemark, null));
            item.setPackingProblems(new ArrayList<>());
            if (matched.isEmpty()) item.getPackingProblems().add("未匹配到装箱明细，请先识别装箱单并核对款号、箱号范围");
            Map<String, Integer> capacities = new LinkedHashMap<>();
            int pairs = 0;
            int cartons = 0;
            for (OrderPackingDetail row : matched) {
                PackingOrderItemResponse previous = packingOwners.putIfAbsent(row.getId(), item);
                if (previous != null && !previous.getKey().equals(item.getKey())) {
                    String problem = "装箱明细与其他选中款重复关联，请核对款号和箱号范围";
                    item.getPackingProblems().add(problem);
                    previous.getPackingProblems().add(problem);
                }
                try {
                    Map<String, Integer> sizes = JSON.readValue(row.getSizeQuantitiesJson(), SIZES);
                    if (sizes == null || sizes.isEmpty() || sizes.values().stream().anyMatch(value -> value == null || value < 0)) {
                        throw new IllegalArgumentException("无效尺码数量");
                    }
                    int capacity = 0;
                    for (Integer quantity : sizes.values()) capacity = Math.addExact(capacity, quantity);
                    if (capacity <= 0 || (row.getCartonCount() != null && row.getCartonCount() < 0)) {
                        throw new IllegalArgumentException("无效装箱数量");
                    }
                    int rowCartons = PackingDetailMatchUtil.getPackingCartonCount(row);
                    int nextPairs = Math.addExact(pairs, Math.multiplyExact(capacity, rowCartons));
                    int nextCartons = Math.addExact(cartons, rowCartons);
                    capacities.merge(String.valueOf(capacity), rowCartons, Math::addExact);
                    pairs = nextPairs;
                    cartons = nextCartons;
                } catch (Exception ex) {
                    item.getPackingProblems().add("装箱明细第" + (row.getRowIndex() == null ? row.getId() : row.getRowIndex())
                            + "行的尺码或箱数无效，请核对装箱单");
                }
            }
            item.setQuantity(pairs);
            item.setCartonCount(cartons);
            item.setPackingCapacities(capacities);
            item.setSourcePackingDetailIds(matched.stream().map(OrderPackingDetail::getId).toList());
            OrderRecordDetail imageSource = sources.stream().filter(source -> text(source.getStyleImagePath()) != null).findFirst().orElse(null);
            String imagePath = imageSource == null ? null : text(imageSource.getStyleImagePath());
            if (imageSource != null) {
                item.setImageUrl("/api/orders/details/" + imageSource.getId() + "/image");
            } else {
                OrderPackingDetail imageRow = matched.stream().filter(row -> text(row.getStyleImagePath()) != null).findFirst().orElse(null);
                if (imageRow != null) {
                    imagePath = text(imageRow.getStyleImagePath());
                    item.setImageUrl("/api/orders/packing-details/" + imageRow.getId() + "/image");
                }
            }
            result.add(new PackingItem(item, imagePath));
        }
        return result;
    }

    public static String groupKey(OrderRecordDetail source) {
        if (source.getOrderId() == null) throw new BusinessException("订单明细缺少来源订单");
        String style = styleKey(source.getDevelopmentNo());
        if (style == null) throw new BusinessException("订单明细缺少有效款号，请核对来源订单");
        return source.getOrderId() + ":" + style;
    }

    private static String override(List<PackingOrderItemRequest> requests, Function<PackingOrderItemRequest, String> field, String fallback) {
        List<String> values = requests.stream().map(field).filter(Objects::nonNull).map(String::trim).distinct().toList();
        if (values.size() > 1) throw new BusinessException("同一订单同款的包装规格或备注不一致，请合并后核对");
        return text(values.isEmpty() ? fallback : values.get(0));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String styleKey(String value) {
        String normalized = DevelopmentNoUtil.normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
