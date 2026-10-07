package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PackingOrderCreateRequest;
import com.shoefactory.assistant.dto.PackingOrderItemRequest;
import com.shoefactory.assistant.dto.PackingOrderItemResponse;
import com.shoefactory.assistant.dto.PackingOrderPreviewRequest;
import com.shoefactory.assistant.dto.PackingOrderTaskResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.PackingOrderTask;
import com.shoefactory.assistant.entity.PackingOrderTaskItem;
import com.shoefactory.assistant.mapper.PackingOrderTaskItemMapper;
import com.shoefactory.assistant.mapper.PackingOrderTaskMapper;
import com.shoefactory.assistant.service.impl.PackingOrderSummaryService;
import com.shoefactory.assistant.service.impl.PackingOrderSummaryService.PackingItem;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PackingOrderTaskService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> CAPACITIES = new TypeReference<>() {};
    private static final TypeReference<List<Long>> IDS = new TypeReference<>() {};
    private final PackingOrderTaskMapper tasks;
    private final PackingOrderTaskItemMapper items;
    private final PackingOrderSummaryService summary;
    private final FileStorageUtil files;
    private final InnerBoxTypeConfigService boxTypes;

    public PackingOrderTaskService(PackingOrderTaskMapper tasks, PackingOrderTaskItemMapper items,
                                   PackingOrderSummaryService summary, FileStorageUtil files, InnerBoxTypeConfigService boxTypes) {
        this.tasks = tasks;
        this.items = items;
        this.summary = summary;
        this.files = files;
        this.boxTypes = boxTypes;
    }

    @Transactional(readOnly = true)
    public List<PackingOrderItemResponse> preview(PackingOrderPreviewRequest request) {
        if (request.getSourceDetailIds() == null || request.getSourceDetailIds().isEmpty()) {
            throw new BusinessException("请选择订单款号");
        }
        List<PackingOrderItemRequest> sources = request.getSourceDetailIds().stream().map(id -> {
            PackingOrderItemRequest item = new PackingOrderItemRequest();
            item.setSourceDetailIds(java.util.Collections.singletonList(id));
            return item;
        }).toList();
        return summary.prepare(sources).stream().map(PackingItem::detail).toList();
    }

    @Transactional
    public PackingOrderTaskResponse create(PackingOrderCreateRequest request) {
        List<PackingItem> prepared = summary.prepare(request.getItems());
        if (prepared.size() != request.getItems().size()) throw new BusinessException("同订单同款请合并为一条订包装明细");
        Map<Long, PackingOrderItemRequest> submittedBySource = request.getItems().stream()
                .flatMap(item -> item.getSourceDetailIds().stream().map(id -> Map.entry(id, item)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        for (PackingItem value : prepared) {
            PackingOrderItemResponse item = value.detail();
            if (!item.getPackingProblems().isEmpty()) {
                throw new BusinessException(item.getDevelopmentNo() + ": " + item.getPackingProblems().get(0));
            }
            if (!hasText(item.getBoxSpec())) throw new BusinessException("请填写 " + item.getDevelopmentNo() + " 的内盒规格");
            var boxType = boxTypes.requireEnabled(item.getInnerBoxTypeId());
            item.setInnerBoxTypeName(boxType.getName());
            PackingOrderItemRequest submitted = item.getSourceDetailIds().stream().map(submittedBySource::get)
                    .filter(Objects::nonNull).findFirst().orElseThrow(() -> new BusinessException("请选择订单款号"));
            if ((submitted.getExpectedPackingCapacities() != null
                    && !submitted.getExpectedPackingCapacities().equals(item.getPackingCapacities()))
                    || (submitted.getExpectedPackingDetailIds() != null
                    && !new LinkedHashSet<>(submitted.getExpectedPackingDetailIds()).equals(new LinkedHashSet<>(item.getSourcePackingDetailIds())))) {
                throw new BusinessException("装箱明细已变化，请刷新装箱汇总后再保存");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        PackingOrderTask task = new PackingOrderTask();
        task.setTaskNo(FileStorageUtil.newBusinessNo("PK"));
        task.setOrderDate(request.getOrderDate() == null ? LocalDate.now() : request.getOrderDate());
        task.setOrderNos(prepared.stream().map(value -> value.detail().getOrderNo()).filter(PackingOrderTaskService::hasText)
                .distinct().collect(Collectors.joining(",")));
        task.setDevelopmentNos(prepared.stream().map(value -> value.detail().getDevelopmentNo()).distinct().collect(Collectors.joining(",")));
        task.setItemCount(prepared.size());
        try {
            task.setTotalPairs(prepared.stream().map(value -> value.detail().getQuantity()).reduce(0, Math::addExact));
            task.setTotalCartonCount(prepared.stream().map(value -> value.detail().getCartonCount()).reduce(0, Math::addExact));
        } catch (ArithmeticException ex) {
            throw new BusinessException("订包装数量过大，请核对装箱明细", ex);
        }
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        tasks.insert(task);
        for (int index = 0; index < prepared.size(); index++) {
            PackingItem value = prepared.get(index);
            PackingOrderItemResponse source = value.detail();
            PackingOrderTaskItem row = new PackingOrderTaskItem();
            row.setTaskId(task.getId());
            row.setLineNo(index + 1);
            row.setOrderId(source.getOrderId());
            row.setOrderNo(source.getOrderNo());
            row.setDevelopmentNo(source.getDevelopmentNo());
            row.setBoxSpec(source.getBoxSpec());
            row.setInnerBoxTypeId(source.getInnerBoxTypeId());
            row.setInnerBoxTypeName(source.getInnerBoxTypeName());
            row.setRemark(source.getRemark());
            row.setQuantity(source.getQuantity());
            row.setCartonCount(source.getCartonCount());
            row.setPackingCapacitiesJson(writeJson(source.getPackingCapacities()));
            row.setSourceDetailIdsJson(writeJson(source.getSourceDetailIds()));
            row.setSourcePackingDetailIdsJson(writeJson(source.getSourcePackingDetailIds()));
            row.setStyleImagePath(value.imagePath());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            items.insert(row);
        }
        return get(task.getId());
    }

    public PageResponse<PackingOrderTaskResponse> list(String orderNo, String developmentNo, long page, long size) {
        Page<PackingOrderTask> result = tasks.selectPage(new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)),
                new LambdaQueryWrapper<PackingOrderTask>()
                        .like(hasText(orderNo), PackingOrderTask::getOrderNos, orderNo)
                        .like(hasText(developmentNo), PackingOrderTask::getDevelopmentNos, developmentNo)
                        .orderByDesc(PackingOrderTask::getCreatedAt).orderByDesc(PackingOrderTask::getId));
        return PageResponse.from(result, result.getRecords().stream().map(task -> PackingOrderTaskResponse.from(task, List.of())).toList());
    }

    public PackingOrderTaskResponse get(Long taskId) {
        PackingOrderTask task = tasks.selectById(taskId);
        if (task == null) throw new BusinessException("订包装记录不存在: " + taskId);
        List<PackingOrderTaskItem> rows = items.selectList(new LambdaQueryWrapper<PackingOrderTaskItem>()
                .eq(PackingOrderTaskItem::getTaskId, taskId).orderByAsc(PackingOrderTaskItem::getLineNo).orderByAsc(PackingOrderTaskItem::getId));
        return PackingOrderTaskResponse.from(task, rows.stream().map(this::toResponse).toList());
    }

    public Path image(Long taskId, Long itemId) {
        PackingOrderTaskItem item = items.selectById(itemId);
        if (item == null || !taskId.equals(item.getTaskId()) || !hasText(item.getStyleImagePath())) {
            throw new BusinessException("订包装图片不存在");
        }
        Path path = files.resolvePath(item.getStyleImagePath());
        files.ensureExists(path);
        return path;
    }

    private PackingOrderItemResponse toResponse(PackingOrderTaskItem row) {
        PackingOrderItemResponse response = new PackingOrderItemResponse();
        response.setId(row.getId());
        response.setKey(row.getOrderId() + ":" + row.getDevelopmentNo());
        response.setOrderId(row.getOrderId());
        response.setOrderNo(row.getOrderNo());
        response.setDevelopmentNo(row.getDevelopmentNo());
        response.setBoxSpec(row.getBoxSpec());
        response.setInnerBoxTypeId(row.getInnerBoxTypeId());
        response.setInnerBoxTypeName(row.getInnerBoxTypeName());
        response.setRemark(row.getRemark());
        response.setQuantity(row.getQuantity());
        response.setCartonCount(row.getCartonCount());
        response.setPackingCapacities(readJson(row.getPackingCapacitiesJson(), CAPACITIES));
        response.setSourceDetailIds(readJson(row.getSourceDetailIdsJson(), IDS));
        response.setSourcePackingDetailIds(readJson(row.getSourcePackingDetailIdsJson(), IDS));
        response.setPackingProblems(List.of());
        response.setImageUrl(hasText(row.getStyleImagePath())
                ? "/api/packing-order-tasks/" + row.getTaskId() + "/items/" + row.getId() + "/image" : null);
        return response;
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException("保存订包装明细失败", ex);
        }
    }

    private static <T> T readJson(String value, TypeReference<T> type) {
        try {
            T data = JSON.readValue(value, type);
            if (data == null) throw new IllegalArgumentException("空记录");
            return data;
        } catch (Exception ex) {
            throw new BusinessException("订包装历史明细无法读取，请联系管理员核对记录", ex);
        }
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
