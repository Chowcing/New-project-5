package com.example.expense.ocr.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.common.config.SecurityConfig;
import com.example.expense.common.security.JwtAuthenticationFilter;
import com.example.expense.common.security.UserPrincipal;
import com.example.expense.common.web.GlobalExceptionHandler;
import com.example.expense.ocr.service.OcrService;
import com.example.expense.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OcrController.class)
@Import({OcrController.class, SecurityConfig.class, GlobalExceptionHandler.class, OcrControllerTest.SecurityBeans.class})
class OcrControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private OcrService ocrService;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private AdminProperties adminProperties;

    @Test
    void recognizeImageReturnsClearErrorWhenOcrDisabled() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "receipt.jpg",
                "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01});
        when(ocrService.recognizeImage(1001L, image)).thenThrow(new IllegalArgumentException("图片转文字功能未启用"));

        mockMvc.perform(multipart("/api/v1/ocr/images")
                        .file(image)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(user(new UserPrincipal(1001L, "demo", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("图片转文字功能未启用"));
    }

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(UserMapper userMapper, AdminProperties adminProperties) {
            return new JwtAuthenticationFilter(mock(com.example.expense.common.security.JwtService.class), userMapper, adminProperties);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
