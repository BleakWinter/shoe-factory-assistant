package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum PrintTaskStatus {
    // 已上传订单，等待用户生成预览/打印。
    PENDING,
    // 后续 print-agent 领取任务后可标记为打印中。
    PRINTING,
    // 打印完成。
    SUCCESS,
    // 打印失败，可带 error_message。
    FAILED,
    // 用户或代理取消。
    CANCELED;

    public static PrintTaskStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task status is required");
        }
        try {
            // 接口入参大小写不敏感，但数据库统一存大写枚举名。
            return PrintTaskStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported task status: " + value, ex);
        }
    }
}
