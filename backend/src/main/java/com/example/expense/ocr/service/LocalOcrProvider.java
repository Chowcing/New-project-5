package com.example.expense.ocr.service;

import com.example.expense.ocr.config.OcrProperties;
import com.example.expense.ocr.dto.OcrTextResponse;
import java.io.IOException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalOcrProvider implements OcrProvider {
    private final OcrProperties ocrProperties;
    private final RestClient restClient;

    public LocalOcrProvider(OcrProperties ocrProperties, RestClient.Builder restClientBuilder) {
        this.ocrProperties = ocrProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public OcrTextResponse recognize(MultipartFile image) {
        try {
            OcrTextResponse response = restClient.post()
                    .uri(ocrUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody(image))
                    .retrieve()
                    .body(OcrTextResponse.class);
            if (response == null) {
                throw new IllegalArgumentException("本地图片转文字服务未返回结果");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("本地图片转文字服务不可用");
        }
    }

    private MultiValueMap<String, Object> multipartBody(MultipartFile image) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(image.getContentType()));
        HttpEntity<ByteArrayResource> imagePart = new HttpEntity<>(imageResource(image), headers);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", imagePart);
        return body;
    }

    private ByteArrayResource imageResource(MultipartFile image) {
        try {
            return new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return originalFilename(image);
                }
            };
        } catch (IOException ex) {
            throw new IllegalArgumentException("图片读取失败");
        }
    }

    private String ocrUrl() {
        String baseUrl = ocrProperties.getLocal().getBaseUrl();
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://localhost:9000" : baseUrl.trim();
        return normalized.replaceAll("/+$", "") + "/ocr";
    }

    private String originalFilename(MultipartFile image) {
        String filename = image.getOriginalFilename();
        return filename == null || filename.isBlank() ? "image" : filename;
    }
}
