package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.OrderRecordDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.enums.FileType;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.mapper.OrderDetailProcessMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.service.OrderExcelImportService;
import com.shoefactory.assistant.service.OrderImportResult;
import com.shoefactory.assistant.service.OrderService;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.StoredFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    // 订单相关 Mapper 只对应三张新表：order_record、order_record_detail、order_detail_process。
    private final OrderRecordMapper orderRecordMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;
    private final OrderDetailProcessMapper orderDetailProcessMapper;
    private final FileStorageUtil fileStorageUtil;
    private final OrderExcelImportService orderExcelImportService;

    public OrderServiceImpl(
            OrderRecordMapper orderRecordMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderDetailProcessMapper orderDetailProcessMapper,
            FileStorageUtil fileStorageUtil,
            OrderExcelImportService orderExcelImportService
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
        this.orderDetailProcessMapper = orderDetailProcessMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.orderExcelImportService = orderExcelImportService;
    }

    @Override
    @Transactional
    public OrderUploadResponse uploadOrderSource(MultipartFile file) {
        // V1 主流程只支持 Excel。图片订单、手工补录留给后续版本。
        String extension = fileStorageUtil.extractExtension(file == null ? null : file.getOriginalFilename());
        FileType fileType = toFileType(extension);
        if (fileType != FileType.EXCEL) {
            throw new BusinessException("V1 仅支持上传 Excel 订单文件");
        }

        // 先把原始 Excel 保存到归档目录，文件名和路径会直接进入 order_record。
        String fileNo = FileStorageUtil.newBusinessNo("SF");
        StoredFile storedFile = fileStorageUtil.saveOriginal(file, fileNo);

        // 导入服务会一次性解析订单主记录、明细行、Excel 内嵌图片。
        OrderImportResult importResult = orderExcelImportService.importOrder(
                fileStorageUtil.resolvePath(storedFile.getPath().toString()),
                storedFile,
                fileNo
        );
        OrderRecord orderRecord = importResult.getOrder();
        orderRecordMapper.insert(orderRecord);
        for (OrderRecordDetail detail : importResult.getDetails()) {
            detail.setOrderId(orderRecord.getId());
            orderRecordDetailMapper.insert(detail);
        }

        return buildUploadResponse(orderRecord, importResult.getDetails());
    }

    @Override
    public PageResponse<OrderRecordResponse> listOrders(
            String orderNo,
            String customerName,
            String developmentNo,
            String recognitionStatus,
            long page,
            long size
    ) {
        OrderRecognitionStatus parsedStatus = OrderRecognitionStatus.parseNullable(recognitionStatus);
        Page<OrderRecord> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<OrderRecord>()
                .like(hasText(orderNo), OrderRecord::getOrderNo, orderNo)
                .like(hasText(customerName), OrderRecord::getCustomerName, customerName)
                .like(hasText(developmentNo), OrderRecord::getDevelopmentNos, developmentNo)
                .eq(parsedStatus != null, OrderRecord::getRecognitionStatus, parsedStatus == null ? null : parsedStatus.getCode())
                .orderByDesc(OrderRecord::getCreatedAt);
        Page<OrderRecord> resultPage = orderRecordMapper.selectPage(pageRequest, wrapper);
        List<OrderRecordResponse> records = resultPage.getRecords().stream()
                .map(OrderRecordResponse::from)
                .toList();
        return PageResponse.from(resultPage, records);
    }

    @Override
    public List<OrderRecordDetailResponse> listOrderDetails(Long orderId) {
        OrderRecord order = orderRecordMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        List<OrderRecordDetail> details = orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                .eq(OrderRecordDetail::getOrderId, orderId)
                .orderByAsc(OrderRecordDetail::getRowIndex)
                .orderByAsc(OrderRecordDetail::getLineNo));
        if (details.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<OrderDetailProcess>> processMap = orderDetailProcessMapper.selectList(new LambdaQueryWrapper<OrderDetailProcess>()
                        .eq(OrderDetailProcess::getOrderId, orderId)
                        .orderByAsc(OrderDetailProcess::getProcessType))
                .stream()
                .collect(Collectors.groupingBy(OrderDetailProcess::getOrderDetailId));
        return details.stream()
                .map(detail -> OrderRecordDetailResponse.from(detail, processMap.getOrDefault(detail.getId(), List.of())))
                .toList();
    }

    @Override
    public Path loadOrderDetailImage(Long detailId) {
        OrderRecordDetail detail = orderRecordDetailMapper.selectById(detailId);
        if (detail == null || detail.getStyleImagePath() == null || detail.getStyleImagePath().isBlank()) {
            throw new BusinessException("订单图片不存在: " + detailId);
        }
        // 数据库里存绝对路径，真正返回给前端前仍要通过 FileStorageUtil 做路径白名单检查。
        Path imagePath = fileStorageUtil.resolvePath(detail.getStyleImagePath());
        fileStorageUtil.ensureExists(imagePath);
        return imagePath;
    }

    private OrderUploadResponse buildUploadResponse(OrderRecord orderRecord, List<OrderRecordDetail> details) {
        OrderUploadResponse response = new OrderUploadResponse();
        response.setOrderId(orderRecord.getId());
        response.setOrderNo(orderRecord.getOrderNo());
        response.setCustomerName(orderRecord.getCustomerName());
        response.setLineCount(details.size());
        response.setTotalPairs(orderRecord.getTotalQuantity());
        // 兼容前端历史字段：打印列表现在直接用订单 id。
        response.setPrintTaskId(orderRecord.getId());
        response.setPrintTaskNo(orderRecord.getOrderNo());
        return response;
    }

    private FileType toFileType(String extension) {
        try {
            return FileType.fromExtension(extension);
        } catch (IllegalArgumentException ex) {
            // FileType 还认识图片扩展名，但业务中 V1 会在 uploadOrderSource 里继续拦截。
            throw new BusinessException("订单原稿仅支持 Excel 或图片文件: " + extension, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
