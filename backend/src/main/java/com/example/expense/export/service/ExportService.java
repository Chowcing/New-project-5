package com.example.expense.export.service;

import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.service.TransactionService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    private final TransactionService transactionService;

    public ExportService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public byte[] exportTransactionsCsv(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            Long categoryId,
            Long accountId,
            String keyword
    ) {
        List<TransactionResponse> rows = transactionService.list(userId, type, startDate, endDate, categoryId, accountId, keyword);
        StringBuilder csv = new StringBuilder();
        // 写入 UTF-8 BOM，便于 Windows Excel 直接识别中文列名和备注。
        csv.append('\uFEFF');
        csv.append("类型,金额,发生时间,分类,账户,备注\n");
        for (TransactionResponse row : rows) {
            csv.append(csvCell(row.getType())).append(',')
                    .append(row.getAmount()).append(',')
                    .append(csvCell(String.valueOf(row.getOccurredAt()))).append(',')
                    .append(csvCell(row.getCategoryName())).append(',')
                    .append(csvCell(row.getAccountName())).append(',')
                    .append(csvCell(row.getNote())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}

