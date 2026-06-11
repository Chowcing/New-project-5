package com.example.expense.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.imports.dto.ImportJobResponse;
import com.example.expense.imports.dto.ImportResult;
import com.example.expense.imports.dto.ImportRowError;
import com.example.expense.imports.entity.ImportJob;
import com.example.expense.imports.mapper.ImportJobMapper;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.service.TransactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private static final int MAX_ROWS = 1000;
    private static final int PROGRESS_UPDATE_INTERVAL = 20;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    private final ImportJobMapper importJobMapper;
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor importTaskExecutor;
    private final Clock clock;
    private final BusinessAuditLogService businessAuditLogService;

    @Autowired
    public ImportService(
            ImportJobMapper importJobMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            TransactionService transactionService,
            ObjectMapper objectMapper,
            @Qualifier("importTaskExecutor") ThreadPoolTaskExecutor importTaskExecutor,
            Clock clock
    ) {
        this(importJobMapper, categoryService, paymentMethodService, transactionService, objectMapper, importTaskExecutor, clock, null);
    }

    public ImportService(
            ImportJobMapper importJobMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            TransactionService transactionService,
            ObjectMapper objectMapper,
            @Qualifier("importTaskExecutor") ThreadPoolTaskExecutor importTaskExecutor,
            Clock clock,
            BusinessAuditLogService businessAuditLogService
    ) {
        this.importJobMapper = importJobMapper;
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
        this.importTaskExecutor = importTaskExecutor;
        this.clock = clock;
        this.businessAuditLogService = businessAuditLogService;
    }

    public ImportJobResponse createTransactionsCsvJob(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 CSV 文件");
        }

        String content = readContent(file);
        String contentHash = sha256(content);
        ImportJob runningJob = importJobMapper.selectOne(new LambdaQueryWrapper<ImportJob>()
                .eq(ImportJob::getUserId, userId)
                .eq(ImportJob::getContentHash, contentHash)
                .in(ImportJob::getStatus, List.of(STATUS_PENDING, STATUS_RUNNING))
                .orderByDesc(ImportJob::getId)
                .last("LIMIT 1"));
        if (runningJob != null) {
            return toResponse(runningJob);
        }

        ImportJob job = new ImportJob();
        job.setUserId(userId);
        job.setOriginalFilename(normalizeFilename(file.getOriginalFilename()));
        job.setContentHash(contentHash);
        job.setCsvContent(content);
        job.setStatus(STATUS_PENDING);
        job.setTotalRows(0);
        job.setImportedRows(0);
        job.setFailedRows(0);
        importJobMapper.insert(job);
        audit(userId, "IMPORT_JOB_CREATE", job.getId());
        enqueueJob(job.getId());
        return getImportJob(userId, job.getId());
    }

    public ImportJobResponse getImportJob(Long userId, Long id) {
        return toResponse(requireOwnedJob(userId, id));
    }

    public void resumeUnfinishedJobs() {
        List<ImportJob> unfinishedJobs = importJobMapper.selectList(new LambdaQueryWrapper<ImportJob>()
                .in(ImportJob::getStatus, List.of(STATUS_PENDING, STATUS_RUNNING))
                .isNotNull(ImportJob::getCsvContent));
        for (ImportJob job : unfinishedJobs) {
            enqueueJob(job.getId());
        }
    }

    public ImportResult importTransactionsCsv(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 CSV 文件");
        }
        return importTransactionsCsvContent(userId, readContent(file), null);
    }

    private void enqueueJob(Long jobId) {
        importTaskExecutor.execute(() -> processImportJob(jobId));
    }

    private void processImportJob(Long jobId) {
        ImportJob job = importJobMapper.selectById(jobId);
        if (job == null || STATUS_SUCCESS.equals(job.getStatus()) || STATUS_FAILED.equals(job.getStatus())) {
            return;
        }
        if (job.getCsvContent() == null || job.getCsvContent().isBlank()) {
            markFailed(jobId, "CSV 内容为空");
            return;
        }

        markRunning(jobId);
        try {
            ImportResult result = importTransactionsCsvContent(job.getUserId(), job.getCsvContent(), new ImportProgressListener() {
                @Override
                public void onTotalRows(int totalRows) {
                    updateProgress(jobId, totalRows, null, null);
                }

                @Override
                public void onProgress(int importedRows, int failedRows) {
                    updateProgress(jobId, null, importedRows, failedRows);
                }
            });
            markSuccess(job.getUserId(), jobId, result);
        } catch (IllegalArgumentException ex) {
            markFailed(jobId, ex.getMessage());
        } catch (Exception ex) {
            log.error("导入交易 CSV 任务失败 jobId={}", jobId, ex);
            markFailed(jobId, "导入失败，请稍后重试");
        }
    }

    private ImportResult importTransactionsCsvContent(Long userId, String content, ImportProgressListener listener) {
        List<List<String>> rows = parseCsv(content);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV 内容为空");
        }

        int startIndex = looksLikeHeader(rows.get(0)) ? 1 : 0;
        int totalRows = Math.max(rows.size() - startIndex, 0);
        if (totalRows > MAX_ROWS) {
            throw new IllegalArgumentException("单次最多导入 " + MAX_ROWS + " 条记录");
        }
        notifyTotalRows(listener, totalRows);

        Map<String, Category> categories = categoryMap(userId);
        Map<String, PaymentMethod> paymentMethods = paymentMethodMap(userId);
        List<ImportRowError> errors = new ArrayList<>();
        int importedRows = 0;
        int handledRows = 0;

        for (int index = startIndex; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (isBlankRow(row)) {
                totalRows--;
                notifyTotalRows(listener, totalRows);
                continue;
            }
            try {
                TransactionRequest request = toRequest(row, categories, paymentMethods);
                if (transactionService.existsSameTransaction(userId, request)) {
                    errors.add(rowError(index + 1, row, "DUPLICATE", "记录已存在，已跳过"));
                } else {
                    transactionService.create(userId, request);
                    importedRows++;
                }
            } catch (IllegalArgumentException ex) {
                errors.add(rowError(index + 1, row, classifyError(ex.getMessage()), ex.getMessage()));
            }
            handledRows++;
            if (handledRows % PROGRESS_UPDATE_INTERVAL == 0 || handledRows == totalRows) {
                notifyProgress(listener, importedRows, errors.size());
            }
        }

        ImportResult result = new ImportResult(totalRows, importedRows, errors.size(), errors);
        notifyProgress(listener, result.importedRows(), result.failedRows());
        log.info(
                "导入交易 CSV userId={} totalRows={} importedRows={} errorRows={}",
                userId,
                result.totalRows(),
                result.importedRows(),
                result.failedRows()
        );
        return result;
    }

    private ImportRowError rowError(int rowNumber, List<String> row, String errorType, String message) {
        return new ImportRowError(
                rowNumber,
                errorType,
                message,
                cell(row, 0),
                cell(row, 1),
                cell(row, 2),
                cell(row, 3),
                cell(row, 4),
                cell(row, 5),
                cell(row, 6),
                cell(row, 7),
                cell(row, 8),
                cell(row, 9)
        );
    }

    private String classifyError(String message) {
        if (message == null || message.isBlank()) {
            return "OTHER";
        }
        if (message.contains("支付方式")) {
            return "PAYMENT_METHOD";
        }
        if (message.contains("分类")) {
            return "CATEGORY";
        }
        if (message.contains("金额")) {
            return "AMOUNT";
        }
        if (message.contains("发生时间")) {
            return "TIME";
        }
        if (message.contains("不能为空")) {
            return "REQUIRED";
        }
        if (message.contains("类型必须")) {
            return "TYPE";
        }
        if (message.contains("渠道必须")) {
            return "CHANNEL";
        }
        if (message.contains("列数不足")) {
            return "ROW_FORMAT";
        }
        return "OTHER";
    }

    private ImportJob requireOwnedJob(Long userId, Long id) {
        ImportJob job = importJobMapper.selectOne(new LambdaQueryWrapper<ImportJob>()
                .eq(ImportJob::getId, id)
                .eq(ImportJob::getUserId, userId));
        if (job == null) {
            throw new IllegalArgumentException("导入任务不存在");
        }
        return job;
    }

    private void markRunning(Long jobId) {
        importJobMapper.update(null, new LambdaUpdateWrapper<ImportJob>()
                .eq(ImportJob::getId, jobId)
                .set(ImportJob::getStatus, STATUS_RUNNING)
                .set(ImportJob::getStartedAt, LocalDateTime.now(clock))
                .set(ImportJob::getErrorMessage, null));
    }

    private void updateProgress(Long jobId, Integer totalRows, Integer importedRows, Integer failedRows) {
        LambdaUpdateWrapper<ImportJob> wrapper = new LambdaUpdateWrapper<ImportJob>()
                .eq(ImportJob::getId, jobId);
        if (totalRows != null) {
            wrapper.set(ImportJob::getTotalRows, totalRows);
        }
        if (importedRows != null) {
            wrapper.set(ImportJob::getImportedRows, importedRows);
        }
        if (failedRows != null) {
            wrapper.set(ImportJob::getFailedRows, failedRows);
        }
        importJobMapper.update(null, wrapper);
    }

    private void markSuccess(Long userId, Long jobId, ImportResult result) {
        importJobMapper.update(null, new LambdaUpdateWrapper<ImportJob>()
                .eq(ImportJob::getId, jobId)
                .set(ImportJob::getStatus, STATUS_SUCCESS)
                .set(ImportJob::getTotalRows, result.totalRows())
                .set(ImportJob::getImportedRows, result.importedRows())
                .set(ImportJob::getFailedRows, result.failedRows())
                .set(ImportJob::getResultJson, writeResult(result))
                .set(ImportJob::getErrorMessage, null)
                .set(ImportJob::getCsvContent, null)
                .set(ImportJob::getFinishedAt, LocalDateTime.now(clock)));
        audit(userId, "IMPORT_JOB_SUCCESS", jobId);
    }

    private void markFailed(Long jobId, String message) {
        importJobMapper.update(null, new LambdaUpdateWrapper<ImportJob>()
                .eq(ImportJob::getId, jobId)
                .set(ImportJob::getStatus, STATUS_FAILED)
                .set(ImportJob::getErrorMessage, message)
                .set(ImportJob::getCsvContent, null)
                .set(ImportJob::getFinishedAt, LocalDateTime.now(clock)));
    }

    private void audit(Long userId, String action, Long targetId) {
        if (businessAuditLogService != null) {
            businessAuditLogService.recordSuccess(userId, action, "IMPORT_JOB", targetId, "IMPORT");
        }
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getOriginalFilename(),
                job.getStatus(),
                defaultInt(job.getTotalRows()),
                defaultInt(job.getImportedRows()),
                defaultInt(job.getFailedRows()),
                readResult(job.getResultJson()),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }

    private String writeResult(ImportResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("导入结果序列化失败");
        }
    }

    private ImportResult readResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(resultJson, ImportResult.class);
        } catch (JsonProcessingException ex) {
            log.warn("导入结果解析失败", ex);
            return null;
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void notifyTotalRows(ImportProgressListener listener, int totalRows) {
        if (listener != null) {
            listener.onTotalRows(totalRows);
        }
    }

    private void notifyProgress(ImportProgressListener listener, int importedRows, int failedRows) {
        if (listener != null) {
            listener.onProgress(importedRows, failedRows);
        }
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
        String itemName = maxLength(trimToNull(cell(row, 1)), "事项", 64);
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
                null,
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

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String normalized = filename.trim().replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }
        if (normalized.length() > 255) {
            return normalized.substring(0, 255);
        }
        return normalized;
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

    private interface ImportProgressListener {
        void onTotalRows(int totalRows);

        void onProgress(int importedRows, int failedRows);
    }
}
