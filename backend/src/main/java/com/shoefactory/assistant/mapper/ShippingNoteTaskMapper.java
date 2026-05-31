package com.shoefactory.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import org.apache.ibatis.annotations.Select;

public interface ShippingNoteTaskMapper extends BaseMapper<ShippingNoteTask> {

    @Select("SELECT COALESCE(SUM(total_pairs), 0) FROM shipping_note_task")
    Integer sumTotalPairs();
}
