package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.StyleConfigResponse;
import com.shoefactory.assistant.dto.StyleConfigSaveRequest;

import java.util.Collection;
import java.util.List;

public interface StyleConfigService {

    PageResponse<StyleConfigResponse> listStyleConfigs(
            String developmentNo,
            Boolean incompleteOnly,
            long page,
            long size
    );

    StyleConfigResponse createStyleConfig(StyleConfigSaveRequest request);

    StyleConfigResponse updateStyleConfig(Long id, StyleConfigSaveRequest request);

    List<String> listUnconfiguredDevelopmentNos();

    void ensureConfigsForDevelopmentNos(Collection<String> developmentNos);
}
