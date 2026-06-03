package com.example.expense.ocr.controller;

import com.example.expense.common.web.ApiResponse;
import com.example.expense.ocr.dto.OcrTextResponse;
import com.example.expense.ocr.service.OcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ocr")
public class OcrController {
    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/images")
    public ApiResponse<OcrTextResponse> recognizeImage(@RequestPart("image") MultipartFile image) {
        return ApiResponse.ok("识别完成", ocrService.recognizeImage(image));
    }
}
