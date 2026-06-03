package com.example.expense.ocr.service;

import com.example.expense.ocr.dto.OcrTextResponse;
import org.springframework.web.multipart.MultipartFile;

public interface OcrProvider {
    String providerName();

    OcrTextResponse recognize(MultipartFile image);
}
