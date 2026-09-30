package com.codeit.otboo.support.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class S3StorageServiceTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"application/octet-stream", "image/png", "image/jpeg"})
    void storesActualJpegTypeRegardlessOfUploadMetadataOrExtension(String metadata) throws Exception {
        S3Client client = mock(S3Client.class);
        S3StorageService service = new S3StorageService(client, mock(S3Presigner.class));
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "clothesPrefix", "clothes");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        byte[] bytes = output.toByteArray();

        service.saveClothes(new MockMultipartFile("image", "misleading.png", metadata, bytes), UUID.randomUUID());

        var request = forClass(PutObjectRequest.class);
        var body = forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        try (var stream = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).containsExactly(bytes);
        }
    }

    @Test
    void rejectsNonImageUploadBeforeWritingToS3() {
        S3Client client = mock(S3Client.class);
        S3StorageService service = new S3StorageService(client, mock(S3Presigner.class));
        var file = new MockMultipartFile("image", "fake.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> service.saveClothes(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot identify image content type");
        verifyNoInteractions(client);
    }

    @Test
    void readsObjectBytesAndContentTypeFromConfiguredBucket() {
        S3Client client = mock(S3Client.class);
        S3StorageService service = new S3StorageService(client, mock(S3Presigner.class));
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        byte[] bytes = {1, 2, 3};
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/png").build(), bytes));

        S3StorageService.StoredObject object = service.getObject("clothes/top.png");

        assertThat(object.bytes()).containsExactly(bytes);
        assertThat(object.contentType()).isEqualTo("image/png");
        verify(client).getObjectAsBytes(GetObjectRequest.builder()
                .bucket("test-bucket").key("clothes/top.png").build());
    }

    @Test
    void storesGeneratedOutfitImageUnderTheOutfitKey() {
        S3Client client = mock(S3Client.class);
        S3StorageService service = new S3StorageService(client, mock(S3Presigner.class));
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "outfitPrefix", "outfits");
        UUID outfitId = UUID.randomUUID();

        String imageKey = service.saveOutfit(new byte[]{1, 2, 3}, "image/webp", outfitId);

        assertThat(imageKey).isEqualTo("outfits/" + outfitId + "/generated.webp");
        var request = forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo(imageKey);
        assertThat(request.getValue().contentType()).isEqualTo("image/webp");
    }
}
