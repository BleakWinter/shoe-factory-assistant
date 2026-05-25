package com.shoefactory.assistant.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerNameUtilTest {

    @Test
    void keepsEnglishCustomerNames() {
        assertThat(CustomerNameUtil.normalizeWithoutChinese("Macy's")).isEqualTo("Macy's");
        assertThat(CustomerNameUtil.normalizeWithoutChinese("Blue Rose Shoe Group")).isEqualTo("Blue Rose Shoe Group");
        assertThat(CustomerNameUtil.normalizeWithoutChinese("HALLS LC")).isEqualTo("HALLS LC");
    }

    @Test
    void removesChineseRemarkAfterCustomerName() {
        assertThat(CustomerNameUtil.normalizeWithoutChinese("ECOM\u5185\u76D2\u52A0\u5E03\u5236\u5E73\u6A61\u7B4B")).isEqualTo("ECOM");
        assertThat(CustomerNameUtil.normalizeWithoutChinese("Nordstrom\u5185\u76D2\u52A0\u677E\u7D27")).isEqualTo("Nordstrom");
    }

    @Test
    void returnsNullWhenOnlyChineseTextRemains() {
        assertThat(CustomerNameUtil.normalizeWithoutChinese("\u5185\u76D2\u52A0\u677E\u7D27")).isNull();
    }

    @Test
    void removesTrailingSeparatorBeforeChineseRemark() {
        assertThat(CustomerNameUtil.normalizeWithoutChinese("ECOM \uFF08\u5185\u76D2\u52A0\u677E\u7D27\uFF09")).isEqualTo("ECOM");
    }
}
