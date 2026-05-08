package com.example.expense.export.service;

import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.service.TransactionService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

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
            String keyword
    ) {
        List<TransactionResponse> rows = transactionService.listAll(userId, type, startDate, endDate, categoryId, keyword);
        StringBuilder csv = new StringBuilder();
        // 写入 UTF-8 BOM，便于 Windows Excel 直接识别中文列名和备注。
        csv.append('\uFEFF');
        csv.append("类型,事项,金额,发生时间,渠道,线上APP,线下地点,支付方式,分类,备注\n");
        for (TransactionResponse row : rows) {
            csv.append(csvCell(row.getType())).append(',')
                    .append(csvCell(row.getItemName())).append(',')
                    .append(row.getAmount()).append(',')
                    .append(csvCell(String.valueOf(row.getOccurredAt()))).append(',')
                    .append(csvCell(row.getChannel())).append(',')
                    .append(csvCell(row.getOnlineApp())).append(',')
                    .append(csvCell(row.getOfflinePlace())).append(',')
                    .append(csvCell(row.getPaymentMethodName())).append(',')
                    .append(csvCell(row.getCategoryName())).append(',')
                    .append(csvCell(row.getNote())).append('\n');
        }
        log.info("导出交易 CSV userId={} rows={}", userId, rows.size());
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
