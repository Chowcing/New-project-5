package com.example.expense.ocr.service;

import com.example.expense.ocr.dto.OcrTextResponse;
import com.example.expense.ocr.config.OcrProperties;
import com.example.expense.transaction.service.TransactionImageService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrService {
    private final TransactionImageService transactionImageService;
    private final OcrProperties ocrProperties;
    private final Map<String, OcrProvider> providers;

    public OcrService(
            TransactionImageService transactionImageService,
            OcrProperties ocrProperties,
            List<OcrProvider> providers
    ) {
        this.transactionImageService = transactionImageService;
        this.ocrProperties = ocrProperties;
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        provider -> normalizeProvider(provider.providerName()),
                        Function.identity()
                ));
    }

    public OcrTextResponse recognizeImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请选择要识别的图片");
        }
        transactionImageService.validateFiles(List.of(image));
        if (!ocrProperties.isEnabled()) {
            throw new IllegalArgumentException("图片转文字功能未启用");
        }
        return provider().recognize(image);
    }

    private OcrProvider provider() {
        String providerName = normalizeProvider(ocrProperties.getProvider());
        OcrProvider provider = providers.get(providerName);
        if (provider == null || "disabled".equals(providerName)) {
            throw new IllegalArgumentException("图片转文字功能未启用");
        }
        return provider;
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "disabled" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
