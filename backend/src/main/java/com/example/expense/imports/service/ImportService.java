package com.example.expense.imports.service;

import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.imports.dto.ImportResult;
import com.example.expense.imports.dto.ImportRowError;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.service.TransactionService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {
    private static final int MAX_ROWS = 1000;
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;
    private final TransactionService transactionService;

    public ImportService(
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            TransactionService transactionService
    ) {
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
        this.transactionService = transactionService;
    }

    public ImportResult importTransactionsCsv(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 CSV 文件");
        }

        List<List<String>> rows = parseCsv(readContent(file));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV 内容为空");
        }

        int startIndex = looksLikeHeader(rows.get(0)) ? 1 : 0;
        int totalRows = Math.max(rows.size() - startIndex, 0);
        if (totalRows > MAX_ROWS) {
            throw new IllegalArgumentException("单次最多导入 " + MAX_ROWS + " 条记录");
        }

        Map<String, Category> categories = categoryMap(userId);
        Map<String, PaymentMethod> paymentMethods = paymentMethodMap(userId);
        List<ImportRowError> errors = new ArrayList<>();
        int importedRows = 0;

        for (int index = startIndex; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (isBlankRow(row)) {
                totalRows--;
                continue;
            }
            try {
                TransactionRequest request = toRequest(row, categories, paymentMethods);
                if (transactionService.existsSameTransaction(userId, request)) {
                    errors.add(new ImportRowError(index + 1, "记录已存在，已跳过"));
                } else {
                    transactionService.create(userId, request);
                    importedRows++;
                }
            } catch (IllegalArgumentException ex) {
                errors.add(new ImportRowError(index + 1, ex.getMessage()));
            }
        }

        return new ImportResult(totalRows, importedRows, errors.size(), errors);
    }

    private TransactionRequest toRequest(
            List<String> row,
            Map<String, Category> categories,
            Map<String, PaymentMethod> paymentMethods
    ) {
        if (row.size() < 10) {
            throw new IllegalArgumentException("列数不足，请使用导出的 CSV 列格式");
        }

        String type = parseType(cell(row, 0));
        String itemName = maxLength(required(cell(row, 1), "事项"), "事项", 64);
        BigDecimal amount = parseAmount(cell(row, 2));
        LocalDateTime occurredAt = parseOccurredAt(cell(row, 3));
        String channel = parseChannel(cell(row, 4));
        String onlineApp = maxLength(trimToNull(cell(row, 5)), "线上APP", 64);
        String offlinePlace = maxLength(trimToNull(cell(row, 6)), "线下地点", 128);
        PaymentMethod paymentMethod = paymentMethods.get(normalizeName(required(cell(row, 7), "支付方式")));
        if (paymentMethod == null) {
            throw new IllegalArgumentException("支付方式不存在：" + cell(row, 7));
        }
        Category category = categories.get(categoryKey(type, required(cell(row, 8), "分类")));
        if (category == null) {
            throw new IllegalArgumentException("分类不存在或类型不匹配：" + cell(row, 8));
        }

        return new TransactionRequest(
                type,
                itemName,
                amount,
                occurredAt,
                channel,
                onlineApp,
                offlinePlace,
                paymentMethod.getId(),
                category.getId(),
                maxLength(trimToNull(cell(row, 9)), "备注", 255)
        );
    }

    private String readContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (IOException ex) {
            throw new IllegalArgumentException("CSV 文件读取失败");
        }
    }

    private Map<String, Category> categoryMap(Long userId) {
        Map<String, Category> map = new HashMap<>();
        for (Category category : categoryService.list(userId, null)) {
            map.putIfAbsent(categoryKey(category.getType(), category.getName()), category);
        }
        return map;
    }

    private Map<String, PaymentMethod> paymentMethodMap(Long userId) {
        Map<String, PaymentMethod> map = new HashMap<>();
        for (PaymentMethod paymentMethod : paymentMethodService.list(userId)) {
            map.putIfAbsent(normalizeName(paymentMethod.getName()), paymentMethod);
        }
        return map;
    }

    private String parseType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("EXPENSE".equals(normalized) || "支出".equals(value.trim())) {
            return "EXPENSE";
        }
        if ("INCOME".equals(normalized) || "收入".equals(value.trim())) {
            return "INCOME";
        }
        throw new IllegalArgumentException("类型必须是 EXPENSE/INCOME 或 支出/收入");
    }

    private String parseChannel(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ONLINE".equals(normalized) || "线上".equals(value.trim())) {
            return "ONLINE";
        }
        if ("OFFLINE".equals(normalized) || "线下".equals(value.trim())) {
            return "OFFLINE";
        }
        throw new IllegalArgumentException("渠道必须是 ONLINE/OFFLINE 或 线上/线下");
    }

    private BigDecimal parseAmount(String value) {
        String trimmed = required(value, "金额");
        if (!trimmed.matches("\\d{1,10}(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("金额最多 10 位整数，并保留 2 位以内小数");
        }
        BigDecimal amount = new BigDecimal(trimmed);
        if (amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        return amount;
    }

    private LocalDateTime parseOccurredAt(String value) {
        String trimmed = required(value, "发生时间").replace(' ', 'T');
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported CSV date format.
            }
        }
        throw new IllegalArgumentException("发生时间格式不正确");
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '"') {
                if (inQuotes && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (current == ',' && !inQuotes) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !inQuotes) {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
            } else {
                field.append(current);
            }
        }

        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private boolean looksLikeHeader(List<String> row) {
        return !row.isEmpty() && ("类型".equals(cell(row, 0).trim()) || "type".equalsIgnoreCase(cell(row, 0).trim()));
    }

    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private String cell(List<String> row, int index) {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).trim();
    }

    private String required(String value, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return trimmed;
    }

    private String maxLength(String value, String label, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(label + "最多 " + maxLength + " 个字符");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String categoryKey(String type, String name) {
        return type + ":" + normalizeName(name);
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
