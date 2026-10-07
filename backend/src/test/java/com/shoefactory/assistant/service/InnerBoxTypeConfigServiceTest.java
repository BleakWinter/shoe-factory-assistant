package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.InnerBoxTypeSaveRequest;
import com.shoefactory.assistant.entity.InnerBoxTypeConfig;
import com.shoefactory.assistant.mapper.InnerBoxTypeConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InnerBoxTypeConfigServiceTest {
    private final InnerBoxTypeConfigMapper mapper = mock(InnerBoxTypeConfigMapper.class);
    private final InnerBoxTypeConfigService service = new InnerBoxTypeConfigService(mapper);

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), InnerBoxTypeConfig.class);
    }

    @Test
    void stoppedOrMissingTypesCannotBeUsedForNewPackingOrders() {
        InnerBoxTypeConfig type = new InnerBoxTypeConfig();
        type.setId(7L); type.setName("标准盒第二版"); type.setEnabled(false);
        when(mapper.selectById(7L)).thenReturn(type);
        assertThatThrownBy(() -> service.requireEnabled(7L)).isInstanceOf(BusinessException.class).hasMessageContaining("已停用");
        assertThatThrownBy(() -> service.requireEnabled(99L)).isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.requireEnabled(null)).isInstanceOf(BusinessException.class).hasMessageContaining("请选择");
        when(mapper.selectBatchIds(List.of(7L))).thenReturn(List.of(type));
        assertThat(service.byIds(List.of(7L)).get(7L)).isSameAs(type);
    }

    @Test
    void enabledTypeCanBeSelected() {
        InnerBoxTypeConfig type = new InnerBoxTypeConfig();
        type.setId(7L); type.setEnabled(true);
        when(mapper.selectById(7L)).thenReturn(type);
        assertThat(service.requireEnabled(7L)).isSameAs(type);
    }

    @Test
    void duplicateVersionNamesAreRejectedBeforeWriting() {
        when(mapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.save(null, new InnerBoxTypeSaveRequest("标准盒第二版", null, true)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("名称已存在");
        verify(mapper, never()).insert(any(InnerBoxTypeConfig.class));
    }

    @Test
    void concurrentDuplicateNamesReturnABusinessError() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(InnerBoxTypeConfig.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.save(null, new InnerBoxTypeSaveRequest("标准盒第二版", null, true)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("名称已存在");
    }
}
