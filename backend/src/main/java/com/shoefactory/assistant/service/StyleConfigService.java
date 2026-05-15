package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShoePriceConfigResponse;
import com.shoefactory.assistant.dto.ShoePriceConfigSaveRequest;
import com.shoefactory.assistant.dto.StyleConfigResponse;
import com.shoefactory.assistant.dto.StyleConfigSaveRequest;

import java.util.Collection;
import java.util.List;

public interface StyleConfigService {

    PageResponse<StyleConfigResponse> listStyleConfigs(
            String developmentNos,
            Boolean incompleteOnly,
            long page,
            long size
    );

    StyleConfigResponse createStyleConfig(StyleConfigSaveRequest request);

    StyleConfigResponse updateStyleConfig(Long id, StyleConfigSaveRequest request);

    PageResponse<ShoePriceConfigResponse> listShoePriceConfigs(
            String developmentNos,
            Boolean incompleteOnly,
            long page,
            long size
    );

    ShoePriceConfigResponse createShoePriceConfig(ShoePriceConfigSaveRequest request);

    ShoePriceConfigResponse updateShoePriceConfig(Long id, ShoePriceConfigSaveRequest request);

    List<String> listUnpricedDevelopmentNos();

    List<String> listUnconfiguredDevelopmentNos();

    List<DevelopmentNoOptionResponse> listDevelopmentNoOptions();

    void ensureConfigsForDevelopmentNos(Collection<String> developmentNos);
}
