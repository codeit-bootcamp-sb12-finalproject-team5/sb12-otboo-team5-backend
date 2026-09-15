package com.codeit.otboo.support.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.clothes-prefix}")
    private String clothesPrefix;

    @Value("${cloud.aws.s3.profile-prefix}")
    private String profilePrefix;

    /**
     * S3에 파일 업로드
     *
     * @return S3 Object Key
     */
    public String saveClothes(MultipartFile file, UUID clothesId) {
        String extension = extractExtension(file.getOriginalFilename());
        String objectKey = clothesPrefix + "/" + clothesId + "/original" + extension;
        saveOne(file, objectKey);
        return objectKey;
    }
    public String saveProfile(MultipartFile file, UUID userId) {
        String extension = extractExtension(file.getOriginalFilename());
        String objectKey = profilePrefix + "/" + userId + "/original" + extension;
        saveOne(file, objectKey);
        return objectKey;
    }
    private void saveOne(MultipartFile file, String objectKey) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(file.getBytes())
            );
        } catch (IOException e) {
            throw new RuntimeException("S3 파일 업로드 실패: " + objectKey, e);
        }
    }


    /**
     * S3 Object 삭제
     *
     * @param objectKey S3 Object Key
     */
    public void deleteOne(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(request);

        } catch (Exception e) {
            throw new RuntimeException("S3 파일 삭제 실패: " + objectKey, e);
        }
    }

    /**
     * S3 Object의 Presigned GET URL 생성
     *
     * @param objectKey S3 Object Key
     * @return 일정 시간 동안 접근 가능한 URL
     */
    public String getPresignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(getObjectRequest)
                        .build();

        PresignedGetObjectRequest presigned =
                s3Presigner.presignGetObject(presignRequest);

        return presigned.url().toString();
    }

    private String extractExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }

        return originalName.substring(
                originalName.lastIndexOf(".")
        );
    }
}
