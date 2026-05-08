package com.example.expense.export.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.export.service.ExportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {
    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/transactions.csv")
    public ResponseEntity<byte[]> transactionsCsv(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword
    ) {
        byte[] content = exportService.exportTransactionsCsv(
                SecurityUtils.currentUserId(), type, startDate, endDate, categoryId, keyword);
        HttpHeaders headers = new HttpHeaders();
        // attachment 文件名和 text/csv charset 同时设置，浏览器下载和 Excel 打开都更稳定。
        headers.setContentDisposition(ContentDisposition.attachment().filename("transactions.csv").build());
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
