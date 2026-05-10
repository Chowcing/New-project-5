package com.example.expense.imports.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.imports.dto.ImportJobResponse;
import com.example.expense.imports.service.ImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ApiResponse<ImportJobResponse> transactionsCsv(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("导入任务已创建", importService.createTransactionsCsvJob(SecurityUtils.currentUserId(), file));
    }

    @GetMapping("/{id}")
    public ApiResponse<ImportJobResponse> getImportJob(@PathVariable Long id) {
        return ApiResponse.ok(importService.getImportJob(SecurityUtils.currentUserId(), id));
    }
}
