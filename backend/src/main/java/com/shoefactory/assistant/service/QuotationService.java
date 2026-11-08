package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.QuotationResponse;
import com.shoefactory.assistant.dto.QuotationSaveRequest;
import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.entity.*;
import com.shoefactory.assistant.mapper.*;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.DevelopmentNoOptionBuilder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuotationService {
    private final ShoeQuotationMapper quotations;
    private final ShoeQuotationItemMapper items;
    private final CustomerConfigService customers;
    private final CustomerPriceService prices;
    private final QuotationImageStore images;
    private final OrderRecordDetailMapper details;
    private final OrderRecordMapper orders;
    private final FileStorageUtil storage;
    public QuotationService(ShoeQuotationMapper quotations, ShoeQuotationItemMapper items,
            CustomerConfigService customers, CustomerPriceService prices, QuotationImageStore images,
            OrderRecordDetailMapper details, OrderRecordMapper orders, FileStorageUtil storage) {
        this.quotations=quotations; this.items=items; this.customers=customers; this.prices=prices;
        this.images=images; this.details=details; this.orders=orders; this.storage=storage;
    }
    @Transactional(readOnly = true)
    public PageResponse<QuotationResponse> list(Long customerId, String style, String developmentNos,
                                              LocalDate startDate, LocalDate endDate, boolean unadoptedOnly, long page, long size) {
        if (startDate!=null && endDate!=null && startDate.isAfter(endDate)) throw new BusinessException("开始日期不能晚于结束日期");
        var styles = developmentNos==null ? List.<String>of() : Arrays.stream(developmentNos.split(","))
                .map(CustomerPriceService::normalize).filter(value->!value.isBlank()).distinct().toList();
        var result = quotations.listLatest(new Page<>(Math.max(1,page),Math.max(1,Math.min(size,100))),
                customerId,CustomerPriceService.normalize(style),styles,startDate,endDate,unadoptedOnly);
        var names = customers.list().stream().collect(Collectors.toMap(CustomerConfig::getId,CustomerConfig::getName));
        var currentPrices = prices.prices();
        return PageResponse.from(result,result.getRecords().stream().map(q -> response(q,q.getVersionNo(),
                names.get(q.getCustomerId()),currentPrices.get(CustomerPriceService.key(q.getCustomerId(),q.getDevelopmentNo())),List.of(),List.of())).toList());
    }
    public List<DevelopmentNoOptionResponse> developmentOptions(Long customerId) {
        return DevelopmentNoOptionBuilder.build(quotations.listLatestDevelopmentNos(customerId).stream()
                .map(CustomerPriceService::normalize).toList());
    }
    @Transactional(readOnly = true)
    public Long forPriceConfig(Long customerId, String developmentNo) {
        customers.require(customerId);
        String style=CustomerPriceService.normalize(developmentNo);
        if(style.isBlank()) throw new BusinessException("请填写款号");
        // 已采用的历史版本也要参与选择；多个采用记录取最近一次，其余按创建顺序取最新版本。
        var quotation=quotations.selectOne(new LambdaQueryWrapper<ShoeQuotation>()
                .select(ShoeQuotation::getId)
                .eq(ShoeQuotation::getCustomerId,customerId).eq(ShoeQuotation::getDevelopmentNo,style)
                .orderByDesc(ShoeQuotation::getAdoptedAt).orderByDesc(ShoeQuotation::getCreatedAt)
                .orderByDesc(ShoeQuotation::getId).last("LIMIT 1"));
        return quotation==null?null:quotation.getId();
    }
    @Transactional(readOnly = true)
    public QuotationResponse get(Long id) {
        ShoeQuotation quotation = require(id);
        var versions = quotations.selectList(new LambdaQueryWrapper<ShoeQuotation>()
                .eq(ShoeQuotation::getVersionGroup,quotation.getVersionGroup()).orderByDesc(ShoeQuotation::getVersionNo));
        String name = quotation.getCustomerId() == null ? null : customers.require(quotation.getCustomerId()).getName();
        return response(quotation,versions.get(0).getVersionNo(),name,
                prices.currentPrice(quotation.getCustomerId(),quotation.getDevelopmentNo()),loadItems(id),versions.stream()
                .map(q -> new QuotationResponse.Version(q.getId(),q.getVersionNo(),q.getFinalPrice(),q.getCreatedAt(),q.getAdoptedAt())).toList());
    }
    public QuotationResponse.Reference reference(Long customerId, String developmentNo) {
        customers.require(customerId);
        String style = CustomerPriceService.normalize(developmentNo);
        if (style.isBlank()) throw new BusinessException("请填写款号");
        var detail = quotations.reference(customerId,style);
        return new QuotationResponse.Reference(detail == null ? null : detail.getId(),
                detail == null || !hasText(detail.getStyleImagePath()) ? null : "/api/orders/details/" + detail.getId() + "/image",
                detail == null ? null : detail.getUpperMaterial(),prices.currentPrice(customerId,style));
    }
    @Transactional
    public QuotationResponse create(QuotationSaveRequest request) {
        return save(request,UUID.randomUUID().toString(),1);
    }
    @Transactional
    public QuotationResponse createVersion(Long sourceId, QuotationSaveRequest request) {
        ShoeQuotation source = require(sourceId);
        ShoeQuotation latest = quotations.lockLatest(source.getVersionGroup());
        if (!Objects.equals(request.expectedVersion(),latest.getVersionNo())) throw new BusinessException("报价已有新版本，请刷新后再保存，当前修改不会覆盖已有版本");
        if ((latest.getCustomerId() != null && !Objects.equals(latest.getCustomerId(),request.customerId()))
                || (hasText(latest.getDevelopmentNo()) && !latest.getDevelopmentNo().equals(CustomerPriceService.normalize(request.developmentNo())))) {
            throw new BusinessException("更改客户或款号请使用“复制为新款”创建新的报价");
        }
        return save(request,latest.getVersionGroup(),latest.getVersionNo()+1);
    }
    private QuotationResponse save(QuotationSaveRequest request, String group, int version) {
        if (request.customerId() != null) customers.require(request.customerId());
        var result = QuotationCalculator.calculate(request);
        var quotation = new ShoeQuotation();
        quotation.setVersionGroup(group); quotation.setVersionNo(version);
        quotation.setCustomerId(request.customerId()); quotation.setDevelopmentNo(CustomerPriceService.normalize(request.developmentNo()));
        quotation.setLastNo(trim(request.lastNo())); quotation.setColorMaterial(trim(request.colorMaterial()));
        quotation.setQuotationDate(request.quotationDate()); quotation.setImageKey(resolveImage(request));
        quotation.setLaborCost(request.laborCost()); quotation.setIncludeOutsole(request.includeOutsole());
        quotation.setRoundingMode(request.roundingMode()); quotation.setManualPrice(request.manualPrice());
        quotation.setFinalPrice(result.finalPrice()); quotation.setMaterialAmount(result.materials());
        quotation.setAuxiliaryAmount(result.auxiliary()); quotation.setOutsoleAmount(result.outsole());
        quotation.setCalculatedAmount(result.calculated()); quotation.setNote(trim(request.note()));
        quotation.setCreatedAt(LocalDateTime.now());
        try { quotations.insert(quotation); }
        catch (DuplicateKeyException ex) { throw new BusinessException("报价已有新版本，请刷新后再保存",ex); }
        for (int index=0; index<request.items().size(); index++) {
            var source = request.items().get(index);
            var item = new ShoeQuotationItem();
            item.setQuotationId(quotation.getId()); item.setCategory(source.category()); item.setSortNo(index);
            item.setPartName(trim(source.partName())); item.setMaterialName(trim(source.materialName()));
            item.setQuantity(source.quantity()); item.setUnit(trim(source.unit())); item.setUnitPrice(source.unitPrice());
            item.setAmount(result.itemAmounts().get(index)); item.setRemark(trim(source.remark()));
            items.insert(item);
        }
        // 此处绝不写客户款价，只有 adopt 才会更新当前单价。
        return get(quotation.getId());
    }
    private String resolveImage(QuotationSaveRequest request) {
        if (hasText(request.imageKey())) { images.require(request.imageKey()); return request.imageKey(); }
        if (request.sourceDetailId() == null) return null;
        OrderRecordDetail detail = details.selectById(request.sourceDetailId());
        OrderRecord order = detail == null ? null : orders.selectById(detail.getOrderId());
        if (order == null || !Objects.equals(order.getCustomerId(),request.customerId())
                || !CustomerPriceService.normalize(detail.getDevelopmentNo()).equals(CustomerPriceService.normalize(request.developmentNo()))) {
            throw new BusinessException("原订单鞋图与报价客户或款号不一致，请重新带入或上传鞋图");
        }
        return hasText(detail.getStyleImagePath()) ? images.copyReference(storage.resolvePath(detail.getStyleImagePath())).imageKey() : null;
    }
    @Transactional
    public QuotationResponse adopt(Long id) {
        ShoeQuotation quotation = require(id);
        ShoeQuotation latest = quotations.lockLatest(quotation.getVersionGroup());
        if (!id.equals(latest.getId())) throw new BusinessException("历史报价请先另存为新版本，再采用为客户单价");
        quotation = quotations.lockById(id);
        if (quotation.getCustomerId() == null || !hasText(quotation.getDevelopmentNo()) || quotation.getQuotationDate() == null) throw new BusinessException("请补齐客户、款号和报价日期后再采用价格");
        if (quotation.getCalculatedAmount() == null || quotation.getFinalPrice() == null) throw new BusinessException("请补齐用量、单价、加工费及最终报价后再采用价格");
        for (var item : loadItems(id)) {
            if ("OUTSOLE".equals(item.getCategory()) && !Boolean.TRUE.equals(quotation.getIncludeOutsole())) continue;
            if (!hasText(item.getPartName()) || !hasText(item.getMaterialName()) || !hasText(item.getUnit())) throw new BusinessException("请补齐报价项目的部位、材料 / 工序及单位");
        }
        prices.adoptQuotationPrice(quotation.getCustomerId(),quotation.getDevelopmentNo(),quotation.getFinalPrice());
        quotations.update(null,new LambdaUpdateWrapper<ShoeQuotation>().eq(ShoeQuotation::getId,id)
                .set(ShoeQuotation::getAdoptedAt,LocalDateTime.now()));
        return get(id);
    }
    private ShoeQuotation require(Long id) {
        ShoeQuotation quotation = quotations.selectById(id);
        if (quotation == null) throw new BusinessException("报价记录不存在");
        return quotation;
    }
    private List<ShoeQuotationItem> loadItems(Long id) {
        return items.selectList(new LambdaQueryWrapper<ShoeQuotationItem>()
                .eq(ShoeQuotationItem::getQuotationId,id).orderByAsc(ShoeQuotationItem::getSortNo));
    }
    private QuotationResponse response(ShoeQuotation q, Integer latestVersion, String customerName,
            java.math.BigDecimal currentPrice, List<ShoeQuotationItem> rows, List<QuotationResponse.Version> versions) {
        return new QuotationResponse(q.getId(),q.getVersionGroup(),q.getVersionNo(),latestVersion,q.getCustomerId(),customerName,
                q.getDevelopmentNo(),q.getLastNo(),q.getColorMaterial(),q.getQuotationDate(),q.getImageKey(),images.url(q.getImageKey()),
                q.getLaborCost(),q.getIncludeOutsole(),q.getRoundingMode(),q.getManualPrice(),q.getFinalPrice(),q.getMaterialAmount(),
                q.getAuxiliaryAmount(),q.getOutsoleAmount(),q.getCalculatedAmount(),currentPrice,q.getNote(),q.getAdoptedAt(),q.getCreatedAt(),rows,versions);
    }
    private static String trim(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static boolean hasText(String text) { return text != null && !text.isBlank(); }
}
