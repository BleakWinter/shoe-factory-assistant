package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.BatchPrintProcessRequest;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.mapper.OrderDetailProcessMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 订单明细处理状态记录，用于标记外箱贴标/内盒贴标等打印操作。
 */
@RestController
@RequestMapping("/api/order-detail-process")
public class OrderDetailProcessController {

    private final OrderDetailProcessMapper orderDetailProcessMapper;

    public OrderDetailProcessController(OrderDetailProcessMapper orderDetailProcessMapper) {
        this.orderDetailProcessMapper = orderDetailProcessMapper;
    }

    @PostMapping("/batch-print")
    public ApiResponse<Void> batchRecordPrint(@RequestBody @Valid BatchPrintProcessRequest request) {
        for (Long detailId : request.getDetailIds()) {
            OrderDetailProcess existing = orderDetailProcessMapper.selectOne(
                new LambdaQueryWrapper<OrderDetailProcess>()
                    .eq(OrderDetailProcess::getOrderDetailId, detailId)
                    .eq(OrderDetailProcess::getProcessType, request.getProcessType())
            );
            if (existing != null) {
                existing.setProcessCount(existing.getProcessCount() + 1);
                existing.setLastProcessAt(LocalDateTime.now());
                existing.setProcessStatus(1);
                orderDetailProcessMapper.updateById(existing);
            } else {
                OrderDetailProcess record = new OrderDetailProcess();
                record.setOrderDetailId(detailId);
                record.setOrderId(request.getOrderId());
                record.setProcessType(request.getProcessType());
                record.setProcessStatus(1);
                record.setProcessCount(1);
                record.setLastProcessAt(LocalDateTime.now());
                orderDetailProcessMapper.insert(record);
            }
        }
        return ApiResponse.ok(null);
    }
}
