package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.CustomerSaveRequest;
import com.shoefactory.assistant.entity.CustomerConfig;
import com.shoefactory.assistant.mapper.CustomerConfigMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.entity.OrderRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerConfigService {
    private final CustomerConfigMapper mapper;
    private final OrderRecordMapper orders;
    public CustomerConfigService(CustomerConfigMapper mapper, OrderRecordMapper orders) {
        this.mapper = mapper;
        this.orders = orders;
    }
    public List<CustomerConfig> list() {
        return mapper.selectList(new LambdaQueryWrapper<CustomerConfig>().orderByAsc(CustomerConfig::getId));
    }
    public CustomerConfig require(Long id) {
        CustomerConfig customer = id == null ? null : mapper.selectById(id);
        if (customer == null) throw new BusinessException("客户不存在");
        return customer;
    }
    public CustomerConfig excelCustomer() {
        CustomerConfig customer = mapper.selectOne(new LambdaQueryWrapper<CustomerConfig>().eq(CustomerConfig::getCode, "DAWEI"));
        if (customer == null) throw new BusinessException("请先执行多客户数据库升级脚本");
        return customer;
    }
    @Transactional
    public CustomerConfig save(Long id, CustomerSaveRequest request) {
        CustomerConfig customer = id == null ? new CustomerConfig() : require(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (id != null && "DAWEI".equals(customer.getCode()) &&
                (!"DAWEI".equals(code) || !"EXCEL".equals(request.inputMode()))) {
            throw new BusinessException("达为的客户编码和 Excel 录单方式不能修改");
        }
        if (id == null && "EXCEL".equals(request.inputMode())) {
            throw new BusinessException("当前 Excel 模板仅支持达为，新客户请使用手工录单");
        }
        if (id != null && !customer.getInputMode().equals(request.inputMode()) &&
                orders.selectCount(new LambdaQueryWrapper<OrderRecord>().eq(OrderRecord::getCustomerId, id)) > 0) {
            throw new BusinessException("已有订单的客户不能修改录单方式");
        }
        if (id != null && !"DAWEI".equals(customer.getCode()) && "EXCEL".equals(request.inputMode())) {
            throw new BusinessException("当前 Excel 模板仅支持达为");
        }
        long duplicate = mapper.selectCount(new LambdaQueryWrapper<CustomerConfig>()
                .eq(CustomerConfig::getCode, code).ne(id != null, CustomerConfig::getId, id));
        if (duplicate > 0) throw new BusinessException("客户编码已存在");
        if (mapper.selectCount(new LambdaQueryWrapper<CustomerConfig>()
                .eq(CustomerConfig::getRecipientName, request.recipientName().trim())
                .ne(id != null, CustomerConfig::getId, id)) > 0) throw new BusinessException("对账名称已被其他客户使用");
        customer.setCode(code);
        customer.setName(request.name().trim());
        customer.setRecipientName(request.recipientName().trim());
        customer.setInputMode(request.inputMode());
        customer.setEnabled(request.enabled());
        customer.setUpdatedAt(LocalDateTime.now());
        if (id == null) { customer.setCreatedAt(customer.getUpdatedAt()); mapper.insert(customer); }
        else mapper.updateById(customer);
        return customer;
    }
}
