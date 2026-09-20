package com.codeit.otboo.support.openai.clothes;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageResolverTest {

    private final ProductImageResolver resolver = new ProductImageResolver();

    @Test
    void prefersOgImageOverPageImages() {
        var document = Jsoup.parse("""
                <html><head><meta property="og:image" content="https://image.msscdn.net/thumbnails/images/goods_img/1/1_main.jpg?w=1200"></head>
                <body><img src="https://image.msscdn.net/thumbnails/images/goods_img/1/1_other.jpg"></body></html>
                """, "https://www.musinsa.com/products/1");

        assertThat(resolver.findImageUrl(document)).contains("https://image.msscdn.net/thumbnails/images/goods_img/1/1_main.jpg?w=1200");
    }

    @Test
    void findsLazyLoadedMusinsaProductImageWhenMetadataIsMissing() {
        var document = Jsoup.parse("""
                <img data-src="//image.msscdn.net/thumbnails/images/goods_img/1/1_main.jpg?w=192">
                """, "https://www.musinsa.com/products/1");

        assertThat(resolver.findImageUrl(document)).contains("https://image.msscdn.net/thumbnails/images/goods_img/1/1_main.jpg?w=192");
    }
}
