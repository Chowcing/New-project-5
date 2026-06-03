package com.example.expense.ocr.service;

import com.example.expense.ocr.dto.OcrTextResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DisabledOcrProvider implements OcrProvider {
    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public OcrTextResponse recognize(MultipartFile image) {
        throw new IllegalArgumentException("图片转文字功能未启用");
    }
}
