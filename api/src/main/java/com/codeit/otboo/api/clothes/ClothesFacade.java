package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.ClothesRequest;
import com.codeit.otboo.api.clothes.dto.ClothesResponse;
import com.codeit.otboo.api.clothes.dto.ClothesUpdateRequest;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisService;
import com.codeit.otboo.support.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesFacade {
    private final ClothesService clothesService;
    private final ClothesAnalysisService clothesAnalysisService;
    private final EmbeddingAsyncService embeddingAsyncService;
    private final S3StorageService s3StorageService;
    private final RestClient restClient;

    public ClothesResponse create(ClothesRequest req, MultipartFile image) {
        Clothes clothes = clothesService.create(req, image);
        embeddingAsyncService.clothesEmbedding(clothes.getId(), clothes.getAttributeText());
        return ClothesResponse.of(clothes, clothes.getUser().getId(), s3StorageService.getPresignedUrl(clothes.getImageUrl()));
    }

    public ClothesResponse update(UUID clothesId, ClothesUpdateRequest req, MultipartFile image) {
        Clothes clothes = clothesService.update(clothesId, req, image);
        embeddingAsyncService.clothesEmbedding(clothes.getId(), clothes.getAttributeText());
        return ClothesResponse.of(clothes, clothes.getUser().getId(), s3StorageService.getPresignedUrl(clothes.getImageUrl()));
    }

    @Async("bulkExecutor")
    public CompletableFuture<ClothesResponse> process(UUID userId, String url, int index, int total) {
        log.info("[{}/{}] WebSearch 시작", index, total);

        ClothesAnalysisResult analysis = clothesAnalysisService.analyze(url);

        log.info("[{}/{}] WebSearch 완료", index, total);

        ResponseEntity<byte[]> response = downloadImage(analysis.imageUrl());
        byte[] image = response.getBody();
        String contentType = Optional.ofNullable(response.getHeaders().getContentType())
                .map(MediaType::toString).orElse("image/webp");
        String extension = contentType.equals("image/webp") ? ".webp" : ".jpg";

        log.info("[{}/{}] 이미지 다운로드 완료", index, total);

        ClothesRequest request = ClothesRequest.of(userId, analysis);

        MultipartFile multipartFile =
                new MockMultipartFile(
                        "image",
                        "clothes" + extension,
                        contentType,
                        image
                );

        ClothesResponse clothesResponse = create(request, multipartFile);

        log.info("[{}/{}] CREATE 완료", index, total);

        return CompletableFuture.completedFuture(clothesResponse);
    }
    private ResponseEntity<byte[]> downloadImage(String imageUrl) {
        try {
            return restClient.get()
                    .uri(imageUrl)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (Exception e) {
            log.error("이미지 다운로드 실패: {}", imageUrl, e);
            throw new RuntimeException("이미지 다운로드 실패: " + imageUrl, e);
        }
    }

}
