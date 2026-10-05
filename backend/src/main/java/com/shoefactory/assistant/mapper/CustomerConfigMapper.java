package com.shoefactory.assistant.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shoefactory.assistant.entity.CustomerConfig;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CustomerConfigMapper extends BaseMapper<CustomerConfig> {
    @Select("SELECT * FROM customer_config WHERE id = #{id} FOR UPDATE")
    CustomerConfig selectForUpdate(Long id);
}
