package com.example.expense.ocr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.expense.ocr.config.OcrProperties;
import com.example.expense.ocr.dto.OcrTextResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LocalOcrProviderTest {
    @Test
    void recognizeForwardsImageToConfiguredLocalService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OcrProperties properties = new OcrProperties();
        properties.getLocal().setBaseUrl("http://ocr-service:9000");
        LocalOcrProvider provider = new LocalOcrProvider(properties, builder);
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "receipt.jpg",
                "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01});
        server.expect(once(), requestTo("http://ocr-service:9000/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("name=\"image\"")))
                .andExpect(content().string(containsString("filename=\"receipt.jpg\"")))
                .andRespond(withSuccess("{\"text\":\"午餐\\n25 元\",\"provider\":\"local-paddleocr\"}", MediaType.APPLICATION_JSON));

        OcrTextResponse response = provider.recognize(image);

        assertThat(response.text()).isEqualTo("午餐\n25 元");
        assertThat(response.provider()).isEqualTo("local-paddleocr");
        server.verify();
    }
}
