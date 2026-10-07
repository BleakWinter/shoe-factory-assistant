package com.shoefactory.assistant.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.*;
import com.shoefactory.assistant.entity.*;
import com.shoefactory.assistant.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomerPriceService {
    private final CustomerPriceConfigMapper mapper;
    private final CustomerConfigService customers;
    private final OrderRecordMapper orders;
    private final OrderRecordDetailMapper details;
    public CustomerPriceService(CustomerPriceConfigMapper mapper, CustomerConfigService customers,
                                OrderRecordMapper orders, OrderRecordDetailMapper details) {
        this.mapper = mapper; this.customers = customers;
        this.orders=orders; this.details=details;
    }
    public record PriceResponse(Long id, Long customerId, String customerName, String developmentNo,
                                BigDecimal shoePrice, boolean complete, LocalDateTime updatedAt, String imageUrl, String upperMaterial) {}
    public void ensure(Long customerId, Collection<String> styles) {
        styles.stream().map(CustomerPriceService::normalize).filter(s->!s.isBlank()).distinct()
                .forEach(style->mapper.ensurePendingPrice(customerId,style));
    }
    public static String normalize(String style) {
        if (style == null) return "";
        return style.trim().toUpperCase(Locale.ROOT).replace('－','-');
    }
    public Map<String, BigDecimal> prices() {
        Map<String, BigDecimal> result = new HashMap<>();
        for (CustomerPriceConfig price : mapper.selectList(new LambdaQueryWrapper<CustomerPriceConfig>())) {
            if (price.getShoePrice() != null) result.put(key(price.getCustomerId(), price.getDevelopmentNo()), price.getShoePrice());
        }
        return result;
    }
    public static String key(Long customerId, String style) { return customerId + ":" + normalize(style); }
    public BigDecimal currentPrice(Long customerId, String style) {
        if (customerId == null || normalize(style).isBlank()) return null;
        CustomerPriceConfig price = mapper.selectOne(new LambdaQueryWrapper<CustomerPriceConfig>()
                .eq(CustomerPriceConfig::getCustomerId,customerId)
                .eq(CustomerPriceConfig::getDevelopmentNo,normalize(style)));
        return price == null ? null : price.getShoePrice();
    }
    @Transactional
    public void adoptQuotationPrice(Long customerId, String style, BigDecimal price) {
        customers.require(customerId);
        String normalized = normalize(style);
        if (normalized.isBlank() || price == null || price.signum() < 0) throw new BusinessException("客户、款号和最终报价必须完整");
        mapper.ensurePendingPrice(customerId,normalized);
        mapper.update(null,new LambdaUpdateWrapper<CustomerPriceConfig>()
                .eq(CustomerPriceConfig::getCustomerId,customerId).eq(CustomerPriceConfig::getDevelopmentNo,normalized)
                .set(CustomerPriceConfig::getShoePrice,price).set(CustomerPriceConfig::getUpdatedAt,LocalDateTime.now()));
    }
    public List<String> unpricedStyles(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<CustomerPriceConfig>()
                .eq(customerId!=null,CustomerPriceConfig::getCustomerId,customerId)
                .isNull(CustomerPriceConfig::getShoePrice)).stream()
                .map(CustomerPriceConfig::getDevelopmentNo).distinct().sorted().toList();
    }
    public List<DevelopmentNoOptionResponse> developmentOptions(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<CustomerPriceConfig>()
                .eq(customerId!=null,CustomerPriceConfig::getCustomerId,customerId)).stream()
                .map(CustomerPriceConfig::getDevelopmentNo).distinct().sorted()
                .map(style->new DevelopmentNoOptionResponse(style,style,List.of())).toList();
    }
    public PageResponse<PriceResponse> list(Long customerId, String developmentNos, Boolean incompleteOnly, long page, long size) {
        var wrapper = new LambdaQueryWrapper<CustomerPriceConfig>()
                .eq(customerId != null, CustomerPriceConfig::getCustomerId, customerId)
                .orderByAsc(CustomerPriceConfig::getCustomerId).orderByAsc(CustomerPriceConfig::getDevelopmentNo);
        if (developmentNos != null && !developmentNos.isBlank()) {
            List<String> styles = Arrays.stream(developmentNos.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
            if (!styles.isEmpty()) wrapper.and(w -> {
                for (int i = 0; i < styles.size(); i++) {
                    if (i > 0) w.or();
                    w.like(CustomerPriceConfig::getDevelopmentNo, styles.get(i));
                }
            });
        }
        if (Boolean.TRUE.equals(incompleteOnly)) wrapper.isNull(CustomerPriceConfig::getShoePrice);
        var result = mapper.selectPage(new Page<>(Math.max(1,page), Math.min(100,Math.max(1,size))), wrapper);
        Map<Long, String> names = customers.list().stream().collect(Collectors.toMap(CustomerConfig::getId, CustomerConfig::getName));
        Map<Long,Long> customerByOrder=orders.selectList(new LambdaQueryWrapper<OrderRecord>()
                .select(OrderRecord::getId,OrderRecord::getCustomerId).eq(customerId!=null,OrderRecord::getCustomerId,customerId))
                .stream().filter(o->o.getCustomerId()!=null).collect(Collectors.toMap(OrderRecord::getId,OrderRecord::getCustomerId));
        List<String> styles=result.getRecords().stream().map(CustomerPriceConfig::getDevelopmentNo).toList();
        Map<String,OrderRecordDetail> references=new HashMap<>();
        if(!styles.isEmpty()&&!customerByOrder.isEmpty()) {
            for (var row:details.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                    .in(OrderRecordDetail::getOrderId,customerByOrder.keySet()).in(OrderRecordDetail::getDevelopmentNo,styles)
                    .orderByDesc(OrderRecordDetail::getCreatedAt))) {
                String key=key(customerByOrder.get(row.getOrderId()),row.getDevelopmentNo());
                references.putIfAbsent(key,row);
                if(row.getStyleImagePath()!=null&&!row.getStyleImagePath().isBlank()
                        && (references.get(key).getStyleImagePath()==null||references.get(key).getStyleImagePath().isBlank())) references.put(key,row);
            }
        }
        return PageResponse.from(result,result.getRecords().stream().map(p->{
            var row=references.get(key(p.getCustomerId(),p.getDevelopmentNo()));
            return new PriceResponse(p.getId(),p.getCustomerId(),names.get(p.getCustomerId()),p.getDevelopmentNo(),
                    p.getShoePrice(),p.getShoePrice()!=null,p.getUpdatedAt(),
                    row==null||row.getStyleImagePath()==null||row.getStyleImagePath().isBlank()?null:"/api/orders/details/"+row.getId()+"/image",
                    row==null?null:row.getUpperMaterial());
        }).toList());
    }
    @Transactional
    public PriceResponse save(Long id, CustomerPriceSaveRequest request) {
        CustomerConfig customer = customers.require(request.customerId());
        String style = normalize(request.developmentNo());
        if (style.isBlank()) throw new BusinessException("款号不能为空");
        CustomerPriceConfig price = id == null ? new CustomerPriceConfig() : mapper.selectById(id);
        if (price == null) throw new BusinessException("价格配置不存在");
        if (id != null && (!request.customerId().equals(price.getCustomerId()) || !style.equals(price.getDevelopmentNo()))) {
            throw new BusinessException("已有价格配置的客户和款号不能修改");
        }
        if (id == null && mapper.selectCount(new LambdaQueryWrapper<CustomerPriceConfig>()
                .eq(CustomerPriceConfig::getCustomerId, request.customerId())
                .eq(CustomerPriceConfig::getDevelopmentNo, style)) > 0) throw new BusinessException("该客户款号已有价格配置，请编辑现有配置");
        price.setCustomerId(customer.getId()); price.setDevelopmentNo(style); price.setShoePrice(request.shoePrice());
        price.setUpdatedAt(LocalDateTime.now());
        if (id == null) { price.setCreatedAt(price.getUpdatedAt()); mapper.insert(price); }
        else mapper.update(null, new LambdaUpdateWrapper<CustomerPriceConfig>().eq(CustomerPriceConfig::getId,id)
                .set(CustomerPriceConfig::getShoePrice,request.shoePrice()).set(CustomerPriceConfig::getUpdatedAt,price.getUpdatedAt()));
        return response(price,customer.getName());
    }
    private PriceResponse response(CustomerPriceConfig p, String name) {
        return new PriceResponse(p.getId(),p.getCustomerId(),name,p.getDevelopmentNo(),p.getShoePrice(),p.getShoePrice()!=null,p.getUpdatedAt(),null,null);
    }
}
