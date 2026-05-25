package com.shoefactory.assistant.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentNoUtilTest {

    @Test
    void normalizesPlainDevelopmentNo() {
        assertThat(DevelopmentNoUtil.normalize("JCD-395-3-01")).isEqualTo("JCD-395-3-01");
    }

    @Test
    void extractsDevelopmentNoBeforeChineseRemark() {
        assertThat(DevelopmentNoUtil.normalize("JCD-402-1-05，新颜色待确认！")).isEqualTo("JCD-402-1-05");
        assertThat(DevelopmentNoUtil.normalize("JCD-253-1-62（新颜色待确认）")).isEqualTo("JCD-253-1-62");
    }

    @Test
    void rejectsTextWithoutValidDevelopmentNo() {
        assertThat(DevelopmentNoUtil.normalize("新颜色待确认")).isNull();
        assertThat(DevelopmentNoUtil.normalize("2026")).isNull();
        assertThat(DevelopmentNoUtil.normalize("JCD")).isNull();
    }

    @Test
    void normalizesCommonHyphenVariants() {
        assertThat(DevelopmentNoUtil.normalize("JCD－395－3－01")).isEqualTo("JCD-395-3-01");
        assertThat(DevelopmentNoUtil.normalize("JCD - 395 - 3 - 01")).isEqualTo("JCD-395-3-01");
    }
}
