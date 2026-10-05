package com.shoefactory.assistant.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shoefactory.assistant.entity.CustomerPriceConfig;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface CustomerPriceConfigMapper extends BaseMapper<CustomerPriceConfig> {
    @org.apache.ibatis.annotations.Insert("INSERT IGNORE INTO customer_price_config (customer_id,development_no) VALUES (#{customerId},#{style})")
    int ensurePendingPrice(@org.apache.ibatis.annotations.Param("customerId") Long customerId,
                           @org.apache.ibatis.annotations.Param("style") String style);
}
