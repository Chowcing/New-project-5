package com.example.expense.ocr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.ocr.config.OcrProperties;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.ocr.dto.OcrTextResponse;
import com.example.expense.transaction.service.TransactionImageService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {
    private static final byte[] JPEG_BYTES = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

    @Mock
    private TransactionImageService transactionImageService;
    @Mock
    private OcrProvider ocrProvider;
    @Mock
    private BusinessAuditLogService businessAuditLogService;

    @Test
    void recognizeImageValidatesFileBeforeCallingProvider() {
        OcrProperties properties = enabledProperties();
        when(ocrProvider.providerName()).thenReturn("test");
        OcrService service = new OcrService(transactionImageService, properties, List.of(ocrProvider), businessAuditLogService);
        MockMultipartFile file = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", JPEG_BYTES);
        when(ocrProvider.recognize(file)).thenReturn(new OcrTextResponse("午餐 25 元", "test"));

        OcrTextResponse response = service.recognizeImage(1001L, file);

        verify(transactionImageService).validateFiles(List.of(file));
        assertThat(response.text()).isEqualTo("午餐 25 元");
        assertThat(response.provider()).isEqualTo("test");
        verify(businessAuditLogService).recordSuccess(1001L, "OCR_IMAGE_RECOGNIZE", "OCR", null, "OCR");
    }

    @Test
    void recognizeImageRejectsEmptyFileWithoutCallingProvider() {
        OcrService service = new OcrService(transactionImageService, enabledProperties(), List.of(ocrProvider));
        MockMultipartFile empty = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.recognizeImage(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请选择要识别的图片");

        verify(ocrProvider, never()).recognize(empty);
    }

    @Test
    void recognizeImageStopsWhenImageValidationFails() {
        OcrService service = new OcrService(transactionImageService, enabledProperties(), List.of(ocrProvider));
        MockMultipartFile text = new MockMultipartFile("image", "a.txt", "text/plain", new byte[] {1});
        doThrow(new IllegalArgumentException("仅支持 JPG、PNG、WebP 图片"))
                .when(transactionImageService).validateFiles(List.of(text));

        assertThatThrownBy(() -> service.recognizeImage(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅支持 JPG、PNG、WebP 图片");

        verify(ocrProvider, never()).recognize(text);
    }

    @Test
    void recognizeImageReturnsClearErrorWhenDisabledAfterValidation() {
        OcrProperties properties = new OcrProperties();
        OcrService service = new OcrService(transactionImageService, properties, List.of(ocrProvider));
        MockMultipartFile file = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        assertThatThrownBy(() -> service.recognizeImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("图片转文字功能未启用");

        verify(transactionImageService).validateFiles(List.of(file));
        verify(ocrProvider, never()).recognize(file);
    }

    private OcrProperties enabledProperties() {
        OcrProperties properties = new OcrProperties();
        properties.setEnabled(true);
        properties.setProvider("test");
        return properties;
    }
}
