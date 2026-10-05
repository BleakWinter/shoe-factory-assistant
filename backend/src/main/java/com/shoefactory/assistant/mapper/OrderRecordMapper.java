package com.shoefactory.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shoefactory.assistant.entity.OrderRecord;

// 订单主表 Mapper，当前无需手写 SQL。
public interface OrderRecordMapper extends BaseMapper<OrderRecord> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM order_record WHERE id = #{id} FOR UPDATE")
    OrderRecord selectForUpdate(Long id);
}
