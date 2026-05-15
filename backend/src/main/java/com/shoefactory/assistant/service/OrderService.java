package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.dto.OrderRecordDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.OrderPackingDetailResponse;
import com.shoefactory.assistant.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface OrderService {

    // 上传订单 Excel，保存原稿、解析主表和明细。
    OrderUploadResponse uploadOrderSource(MultipartFile file);

    // 查询订单主表，页面订单列表和打印列表都以它为入口。
    PageResponse<OrderRecordResponse> listOrders(
            String orderNo,
            String developmentNos,
            String recognitionStatus,
            long page,
            long size
    );

    List<DevelopmentNoOptionResponse> listDevelopmentNoOptions();

    OrderRecordResponse recognizeOrder(Long orderId);

    OrderRecordResponse recognizePacking(Long orderId);

    // 查询某个订单下的明细，并附带每条明细的处理状态。
    List<OrderRecordDetailResponse> listOrderDetails(Long orderId);

    // 查询某个订单下的装箱单明细。
    List<OrderPackingDetailResponse> listOrderPackingDetails(Long orderId);

    // 加载某一行订单明细对应的鞋图。
    Path loadOrderDetailImage(Long detailId);

    // 加载某一行装箱单明细对应的鞋图。
    Path loadOrderPackingDetailImage(Long detailId);
}
