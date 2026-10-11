package com.shoefactory.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShoeQuotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ShoeQuotationMapper extends BaseMapper<ShoeQuotation> {
    @Select("""
            <script>
            SELECT q.* FROM shoe_quotation q
            WHERE NOT EXISTS (SELECT 1 FROM shoe_quotation newer
                WHERE newer.version_group=q.version_group AND newer.version_no&gt;q.version_no)
            <if test="customerId != null">AND q.customer_id=#{customerId}</if>
            <if test="style != null and style != ''">AND q.development_no LIKE CONCAT('%',#{style},'%')</if>
            <if test="styles != null and !styles.isEmpty()">
                AND q.development_no IN
                <foreach collection="styles" item="developmentNo" open="(" separator="," close=")">#{developmentNo}</foreach>
            </if>
            <if test="startDate != null">AND q.quotation_date&gt;=#{startDate}</if>
            <if test="endDate != null">AND q.quotation_date&lt;=#{endDate}</if>
            ORDER BY q.created_at DESC,q.id DESC
            </script>
            """)
    Page<ShoeQuotation> listLatest(Page<ShoeQuotation> page, @Param("customerId") Long customerId,
                                   @Param("style") String style, @Param("styles") List<String> styles,
                                   @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("""
            <script>
            SELECT DISTINCT q.development_no FROM shoe_quotation q
            WHERE NOT EXISTS (SELECT 1 FROM shoe_quotation newer
                WHERE newer.version_group=q.version_group AND newer.version_no&gt;q.version_no)
            AND q.development_no IS NOT NULL AND q.development_no!=''
            <if test="customerId != null">AND q.customer_id=#{customerId}</if>
            </script>
            """)
    List<String> listLatestDevelopmentNos(@Param("customerId") Long customerId);

    @Select("SELECT * FROM shoe_quotation WHERE version_group=#{group} ORDER BY version_no DESC LIMIT 1 FOR UPDATE")
    ShoeQuotation lockLatest(@Param("group") String group);

    @Select("SELECT * FROM shoe_quotation WHERE id=#{id} FOR UPDATE")
    ShoeQuotation lockById(@Param("id") Long id);

    @Select("""
            SELECT d.* FROM order_record_detail d JOIN order_record o ON o.id=d.order_id
            WHERE o.customer_id=#{customerId} AND d.development_no=#{style}
            ORDER BY (d.style_image_path IS NOT NULL AND d.style_image_path!='') DESC,d.created_at DESC,d.id DESC LIMIT 1
            """)
    OrderRecordDetail reference(@Param("customerId") Long customerId, @Param("style") String style);
}
