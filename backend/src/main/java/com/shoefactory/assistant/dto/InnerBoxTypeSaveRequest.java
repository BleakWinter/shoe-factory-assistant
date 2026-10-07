package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InnerBoxTypeSaveRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String remark,
        @NotNull Boolean enabled
) {}
