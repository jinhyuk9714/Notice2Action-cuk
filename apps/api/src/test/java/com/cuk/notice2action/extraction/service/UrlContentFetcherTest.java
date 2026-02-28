package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UrlContentFetcherTest {

  private final UrlContentFetcher fetcher = new UrlContentFetcher();

  @Test
  void rejects_non_http_url() {
    assertThatThrownBy(() -> fetcher.fetch("ftp://example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("http 또는 https");
  }

  @Test
  void rejects_null_url() {
    assertThatThrownBy(() -> fetcher.fetch(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("http 또는 https");
  }
}
