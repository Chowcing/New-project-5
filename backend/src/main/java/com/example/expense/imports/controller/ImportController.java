package com.example.expense.imports.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.imports.dto.ImportResult;
import com.example.expense.imports.service.ImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {
    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/transactions.csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportResult> transactionsCsv(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("导入完成", importService.importTransactionsCsv(SecurityUtils.currentUserId(), file));
    }
}
