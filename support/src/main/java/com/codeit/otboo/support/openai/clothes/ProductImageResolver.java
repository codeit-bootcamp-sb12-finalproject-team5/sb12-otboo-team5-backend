package com.codeit.otboo.support.openai.clothes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/** 상품 페이지에서 대표 이미지 URL을 결정적으로 추출한다. */
@Component
public class ProductImageResolver {

    private static final List<String> META_IMAGE_SELECTORS = List.of(
            "meta[property=og:image]",
            "meta[name=og:image]",
            "meta[name=twitter:image]",
            "meta[property=twitter:image]"
    );

    public String resolve(String productUrl) {
        URI productUri = parseMusinsaProductUrl(productUrl);
        try {
            Document document = Jsoup.connect(productUri.toString())
                    .userAgent("Mozilla/5.0 (compatible; OtbooProductImageResolver/1.0)")
                    .referrer("https://www.musinsa.com/")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get();
            return findImageUrl(document)
                    .orElseThrow(() -> new ProductImageResolutionException("상품 페이지에서 대표 이미지 URL을 찾을 수 없습니다."));
        } catch (IOException e) {
            throw new ProductImageResolutionException("상품 페이지의 대표 이미지 URL 조회에 실패했습니다.", e);
        }
    }

    Optional<String> findImageUrl(Document document) {
        for (String selector : META_IMAGE_SELECTORS) {
            Element metaImage = document.selectFirst(selector);
            if (metaImage != null) {
                Optional<String> imageUrl = toAbsoluteHttpUrl(document, metaImage.attr("content"));
                if (imageUrl.isPresent()) {
                    return imageUrl;
                }
            }
        }

        for (Element image : document.select("img[src], img[data-src], img[data-original], img[data-lazy-src]")) {
            for (String attribute : List.of("src", "data-src", "data-original", "data-lazy-src")) {
                Optional<String> imageUrl = toAbsoluteHttpUrl(document, image.attr(attribute));
                if (imageUrl.filter(this::isMusinsaProductImage).isPresent()) {
                    return imageUrl;
                }
            }
        }
        return Optional.empty();
    }

    private URI parseMusinsaProductUrl(String productUrl) {
        try {
            URI uri = URI.create(productUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals("musinsa.com") || host.endsWith(".musinsa.com"))) {
                throw new ProductImageResolutionException("무신사 상품 URL만 지원합니다.");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new ProductImageResolutionException("유효하지 않은 상품 URL입니다.", e);
        }
    }

    private Optional<String> toAbsoluteHttpUrl(Document document, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            URI imageUri = URI.create(document.baseUri()).resolve(value);
            if (("https".equalsIgnoreCase(imageUri.getScheme()) || "http".equalsIgnoreCase(imageUri.getScheme()))
                    && imageUri.getHost() != null) {
                return Optional.of(imageUri.toString());
            }
        } catch (IllegalArgumentException ignored) {
            // 다음 이미지 후보를 확인한다.
        }
        return Optional.empty();
    }

    private boolean isMusinsaProductImage(String imageUrl) {
        URI uri = URI.create(imageUrl);
        return "image.msscdn.net".equalsIgnoreCase(uri.getHost())
                && uri.getPath().contains("/images/goods_img/");
    }
}
