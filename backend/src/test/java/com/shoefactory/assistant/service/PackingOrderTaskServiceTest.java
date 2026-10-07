package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PackingOrderCreateRequest;
import com.shoefactory.assistant.dto.PackingOrderItemRequest;
import com.shoefactory.assistant.dto.PackingOrderItemResponse;
import com.shoefactory.assistant.dto.PackingOrderPreviewRequest;
import com.shoefactory.assistant.entity.PackingOrderTask;
import com.shoefactory.assistant.entity.InnerBoxTypeConfig;
import com.shoefactory.assistant.entity.PackingOrderTaskItem;
import com.shoefactory.assistant.mapper.PackingOrderTaskItemMapper;
import com.shoefactory.assistant.mapper.PackingOrderTaskMapper;
import com.shoefactory.assistant.service.impl.PackingOrderSummaryService;
import com.shoefactory.assistant.service.impl.PackingOrderSummaryService.PackingItem;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PackingOrderTaskServiceTest {
    private final PackingOrderTaskMapper tasks = mock(PackingOrderTaskMapper.class);
    private final PackingOrderTaskItemMapper items = mock(PackingOrderTaskItemMapper.class);
    private final PackingOrderSummaryService summary = mock(PackingOrderSummaryService.class);
    private final FileStorageUtil files = mock(FileStorageUtil.class);
    private final InnerBoxTypeConfigService boxTypes = mock(InnerBoxTypeConfigService.class);
    private final PackingOrderTaskService service = new PackingOrderTaskService(tasks, items, summary, files, boxTypes);
    private InnerBoxTypeConfig configuredType;
    private final List<PackingOrderTaskItem> storedItems = new ArrayList<>();
    private PackingOrderTask storedTask;
    private PackingOrderItemResponse prepared;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PackingOrderTaskItem.class);
    }

    @BeforeEach
    void setUp() {
        prepared = new PackingOrderItemResponse();
        prepared.setKey("101:JCD-205-3-17");
        prepared.setSourceDetailIds(List.of(11L, 12L));
        prepared.setOrderId(101L);
        prepared.setOrderNo("INV-A");
        prepared.setDevelopmentNo("JCD-205-3-17");
        prepared.setBoxSpec("31×21×11");
        prepared.setInnerBoxTypeId(7L);
        prepared.setInnerBoxTypeName("标准盒第一版");
        configuredType = new InnerBoxTypeConfig();
        configuredType.setId(7L);
        configuredType.setName("标准盒第一版");
        configuredType.setEnabled(true);
        when(boxTypes.requireEnabled(7L)).thenReturn(configuredType);
        when(boxTypes.requireEnabled(null)).thenThrow(new BusinessException("请选择内盒类型"));
        prepared.setRemark("先订内盒");
        prepared.setQuantity(140);
        prepared.setCartonCount(12);
        prepared.setPackingCapacities(Map.of("12", 10, "10", 2));
        prepared.setSourcePackingDetailIds(List.of(21L, 22L));
        prepared.setPackingProblems(new ArrayList<>());
        when(summary.prepare(any())).thenReturn(List.of(new PackingItem(prepared, "2026/10/images/style-a.png")));
        when(tasks.insert(any(PackingOrderTask.class))).thenAnswer(call -> {
            storedTask = call.getArgument(0);
            storedTask.setId(42L);
            return 1;
        });
        when(tasks.selectById(42L)).thenAnswer(call -> storedTask);
        when(items.insert(any(PackingOrderTaskItem.class))).thenAnswer(call -> {
            PackingOrderTaskItem item = call.getArgument(0);
            item.setId(90L + storedItems.size());
            storedItems.add(item);
            return 1;
        });
        when(items.selectList(any())).thenAnswer(call -> List.copyOf(storedItems));
        when(items.selectById(any(Long.class))).thenAnswer(call -> storedItems.stream()
                .filter(item -> item.getId().equals(call.getArgument(0))).findFirst().orElse(null));
    }

    @Test
    void savesPackingFieldsAndAllMergedSourceReferencesInDedicatedColumns() {
        var response = service.create(request());

        assertThat(response.getOrderDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.getTaskNo()).startsWith("PK");
        assertThat(response.getItemCount()).isEqualTo(1);
        assertThat(response.getTotalPairs()).isEqualTo(140);
        assertThat(response.getTotalCartonCount()).isEqualTo(12);
        var item = response.getItems().get(0);
        assertThat(item.getBoxSpec()).isEqualTo("31×21×11");
        assertThat(item.getInnerBoxTypeId()).isEqualTo(7L);
        assertThat(item.getInnerBoxTypeName()).isEqualTo("标准盒第一版");
        assertThat(item.getRemark()).isEqualTo("先订内盒");
        assertThat(item.getPackingCapacities()).isEqualTo(Map.of("12", 10, "10", 2));
        assertThat(item.getSourceDetailIds()).containsExactly(11L, 12L);
        assertThat(item.getSourcePackingDetailIds()).containsExactly(21L, 22L);
        assertThat(item.getKey()).isEqualTo("101:JCD-205-3-17");
        assertThat(item.getImageUrl()).isEqualTo("/api/packing-order-tasks/42/items/90/image");
        assertThat(storedItems.get(0).getBoxSpec()).isEqualTo("31×21×11");
        assertThat(storedItems.get(0).getSourceDetailIdsJson()).isEqualTo("[11,12]");
        assertThat(storedItems.get(0).getOrderId()).isEqualTo(101L);
    }

    @Test
    void historyDoesNotReadChangedOrDeletedSourceDetails() {
        service.create(request());
        prepared.setQuantity(999);
        prepared.setDevelopmentNo("CHANGED");
        prepared.setBoxSpec("CHANGED");
        configuredType.setName("标准盒第二版");
        clearInvocations(summary, boxTypes);

        var response = service.get(42L);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(140);
        assertThat(response.getItems().get(0).getDevelopmentNo()).isEqualTo("JCD-205-3-17");
        assertThat(response.getItems().get(0).getBoxSpec()).isEqualTo("31×21×11");
        assertThat(response.getItems().get(0).getInnerBoxTypeName()).isEqualTo("标准盒第一版");
        verifyNoInteractions(summary, boxTypes);
    }

    @Test
    void savesAllSourceRowsWhenTheSelectedAnchorIsNotTheFirstMergedRow() {
        PackingOrderCreateRequest request = request();
        request.getItems().get(0).setSourceDetailIds(List.of(12L));
        var response = service.create(request);
        assertThat(response.getItems().get(0).getSourceDetailIds()).containsExactly(11L, 12L);
        assertThat(storedItems.get(0).getSourceDetailIdsJson()).isEqualTo("[11,12]");
    }

    @Test
    void sourceChangesAfterPreviewRequireRefreshBeforeAnyWrite() {
        PackingOrderCreateRequest request = request();
        request.getItems().get(0).setExpectedPackingCapacities(Map.of("12", 1));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新装箱汇总");
        request.getItems().get(0).setExpectedPackingCapacities(prepared.getPackingCapacities());
        request.getItems().get(0).setExpectedPackingDetailIds(List.of(23L, 24L));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新装箱汇总");
        verify(tasks, never()).insert(any(PackingOrderTask.class));
        verify(items, never()).insert(any(PackingOrderTaskItem.class));
    }

    @Test
    void invalidPackingOrMissingInnerBoxCannotBeSaved() {
        prepared.getPackingProblems().add("未匹配到装箱明细");
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("未匹配");
        prepared.getPackingProblems().clear();
        prepared.setBoxSpec(" ");
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("内盒规格");
        verify(tasks, never()).insert(any(PackingOrderTask.class));
    }

    @Test
    void missingOrDisabledInnerBoxTypeCannotBeSaved() {
        prepared.setInnerBoxTypeId(null);
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择内盒类型");
        prepared.setInnerBoxTypeId(7L);
        when(boxTypes.requireEnabled(7L)).thenThrow(new BusinessException("内盒类型已停用，请重新选择"));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");
        verify(tasks, never()).insert(any(PackingOrderTask.class));
    }

    @Test
    void typeNameIsResolvedByServerRatherThanTrustedFromPreview() {
        prepared.setInnerBoxTypeName("伪造的类型名");
        assertThat(service.create(request()).getItems().get(0).getInnerBoxTypeName()).isEqualTo("标准盒第一版");
    }

    @Test
    void previewDoesNotCreateRecords() {
        PackingOrderPreviewRequest request = new PackingOrderPreviewRequest();
        request.setSourceDetailIds(List.of(11L, 12L));
        assertThat(service.preview(request)).containsExactly(prepared);
        verifyNoInteractions(tasks, items);
    }

    @Test
    void historicalImageUsesSavedPathAndChecksThatTheItemBelongsToTheTask() {
        service.create(request());
        Path path = Path.of("/private/tmp/history.png");
        when(files.resolvePath("2026/10/images/style-a.png")).thenReturn(path);
        clearInvocations(summary);

        assertThat(service.image(42L, 90L)).isEqualTo(path);
        verify(files).ensureExists(path);
        assertThatThrownBy(() -> service.image(99L, 90L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(summary);
    }

    @Test
    void damagedSavedSummaryIsNotSilentlyReplacedWithLiveSourceData() {
        service.create(request());
        storedItems.get(0).setPackingCapacitiesJson("broken-json");
        clearInvocations(summary);
        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史明细无法读取");
        verifyNoInteractions(summary);
    }

    @Test
    void rejectsMultipleSubmittedItemsForTheSameOrderAndStyleBeforeWriting() {
        PackingOrderCreateRequest request = request();
        request.setItems(List.of(request.getItems().get(0), request.getItems().get(0)));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("合并为一条");
        verify(tasks, never()).insert(any(PackingOrderTask.class));
    }

    private PackingOrderCreateRequest request() {
        PackingOrderItemRequest item = new PackingOrderItemRequest();
        item.setSourceDetailIds(List.of(11L, 12L));
        item.setBoxSpec("31×21×11");
        item.setInnerBoxTypeId(7L);
        item.setRemark("先订内盒");
        item.setExpectedPackingCapacities(Map.of("12", 10, "10", 2));
        item.setExpectedPackingDetailIds(List.of(21L, 22L));
        PackingOrderCreateRequest request = new PackingOrderCreateRequest();
        request.setOrderDate(LocalDate.of(2026, 10, 7));
        request.setItems(List.of(item));
        return request;
    }
}
