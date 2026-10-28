package com.mywebsite.site.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 全文搜索的 LIKE 模式转义：用户输入里的通配符必须按字面量匹配，不能放大结果集。 */
class SiteAiSearchPatternTest {

    @Test
    void wrapsPlainQuery() {
        assertThat(SiteAiConversationServiceImpl.likePattern("關鍵")).isEqualTo("%關鍵%");
    }

    @Test
    void escapesPercentAndUnderscore() {
        assertThat(SiteAiConversationServiceImpl.likePattern("50%_")).isEqualTo("%50\\%\\_%");
    }

    @Test
    void escapesBackslashFirst() {
        assertThat(SiteAiConversationServiceImpl.likePattern("a\\b")).isEqualTo("%a\\\\b%");
    }
}
