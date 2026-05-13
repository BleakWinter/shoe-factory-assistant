package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintTaskCreateRequest;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.enums.PrintType;

import java.util.List;

public interface PrintTaskService {

    // 早期确认打印接口：从已有预览创建任务。
    PrintTaskResponse createTask(PrintTaskCreateRequest request);

    // 打印列表页面查询全部任务。
    List<PrintTaskResponse> listTasks();

    // 本地打印代理轮询待打印任务。
    List<PrintTaskResponse> listPendingTasks(int limit);

    // 当前主流程：对已有任务生成订单/装箱单 PDF 预览。
    PrintPreviewResponse generateTaskPreview(Long taskId, PrintType printType);

    // 强制重新生成订单/装箱单 PDF 预览。
    PrintPreviewResponse regenerateTaskPreview(Long taskId, PrintType printType);

    // 本地打印代理回写任务状态。
    PrintTaskResponse updateTaskStatus(Long id, PrintTaskStatusUpdateRequest request);
}
