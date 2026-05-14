package com.shoefactory.assistant.service;

import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.util.StoredFile;

import java.nio.file.Path;
import java.util.List;

public interface OrderExcelImportService {

    // 上传时只读取列表需要的主表摘要，不落订单明细或装箱单明细。
    OrderRecord readOrderSummary(Path sourceExcel, StoredFile storedFile);

    // 用户点击“识别订单”时解析订单 sheet 明细。
    OrderImportResult importOrderDetails(Path sourceExcel, StoredFile storedFile, String fileNo);

    // 用户点击“识别装箱单”时解析装箱单 sheet 明细。
    List<OrderPackingDetail> importPackingDetails(Path sourceExcel, OrderRecord order, String fileNo);
}
