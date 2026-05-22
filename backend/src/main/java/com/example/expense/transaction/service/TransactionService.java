package com.example.expense.transaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.web.PageResponse;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayCardsResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionMapper transactionMapper;
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;

    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService
    ) {
        this.transactionMapper = transactionMapper;
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
    }

    public PageResponse<TransactionResponse> list(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            Long categoryId,
            Long paymentMethodId,
            String keyword,
            int page,
            int size
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        long total = transactionMapper.countRecords(userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        long offset = (long) (page - 1) * size;
        // 所有列表和导出查询统一从 Mapper 注入 userId 条件，避免前端传参造成跨用户读取。
        List<TransactionResponse> rows = transactionMapper.selectRecords(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword, size, offset);
        return PageResponse.of(rows, total, page, size);
    }

    public TransactionDayCardsResponse dailyCards(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            Long categoryId,
            Long paymentMethodId,
            String keyword,
            int dayPage,
            int daySize,
            int recordPage,
            int recordSize
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        long totalRecords = transactionMapper.countRecords(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        long totalDays = transactionMapper.countRecordDays(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        long dayOffset = (long) (dayPage - 1) * daySize;
        List<TransactionDayCardResponse> days = transactionMapper.selectDayCards(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword, daySize, dayOffset);
        long recordOffset = (long) (recordPage - 1) * recordSize;

        for (TransactionDayCardResponse day : days) {
            BigDecimal totalExpense = defaultMoney(day.getTotalExpense());
            BigDecimal totalIncome = defaultMoney(day.getTotalIncome());
            day.setTotalExpense(totalExpense);
            day.setTotalIncome(totalIncome);
            day.setBalance(totalIncome.subtract(totalExpense));

            LocalDateTime dayStart = day.getDate().atStartOfDay();
            LocalDateTime dayEnd = day.getDate().plusDays(1).atStartOfDay();
            List<TransactionResponse> rows = transactionMapper.selectRecords(
                    userId, type, dayStart, dayEnd, channel, categoryId, paymentMethodId, keyword, recordSize, recordOffset);
            day.setRecords(PageResponse.of(rows, day.getTransactionCount(), recordPage, recordSize));
        }

        return TransactionDayCardsResponse.of(days, totalDays, totalRecords, dayPage, daySize);
    }

    public List<TransactionDayOptionResponse> dailyOptions(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            Long categoryId,
            Long paymentMethodId,
            String keyword
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<TransactionDayOptionResponse> days = transactionMapper.selectDayOptions(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        for (TransactionDayOptionResponse day : days) {
            BigDecimal totalExpense = defaultMoney(day.getTotalExpense());
            BigDecimal totalIncome = defaultMoney(day.getTotalIncome());
            day.setTotalExpense(totalExpense);
            day.setTotalIncome(totalIncome);
            day.setBalance(totalIncome.subtract(totalExpense));
        }
        return days;
    }

    public List<TransactionResponse> listAll(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            Long categoryId,
            String keyword
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        return transactionMapper.selectRecords(userId, type, startAt, endAt, null, categoryId, null, keyword, null, null);
    }

    public TransactionResponse get(Long userId, Long id) {
        TransactionResponse response = transactionMapper.selectRecord(userId, id);
        if (response == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        return response;
    }

    public List<TransactionTemplateResponse> recommendTemplates(Long userId, String type, int limit) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedType = blankToNull(type);
        List<TransactionResponse> rows = transactionMapper.selectRecords(
                userId, normalizedType, now.minusDays(180), now, null, null, null, null, 300, 0L);
        if (rows.isEmpty()) {
            rows = transactionMapper.selectRecords(userId, normalizedType, null, now, null, null, null, null, 300, 0L);
        }

        Map<String, TemplateCandidate> candidates = new HashMap<>();
        for (TransactionResponse row : rows) {
            if (!hasActiveReferences(userId, row)) {
                continue;
            }
            String key = templateKey(row);
            TemplateCandidate candidate = candidates.computeIfAbsent(key, ignored -> new TemplateCandidate(row));
            candidate.add(row, now);
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(TemplateCandidate::score).reversed())
                .limit(limit)
                .map(TemplateCandidate::toResponse)
                .toList();
    }

    public List<TransactionTemplateResponse> recommendContextTemplates(
            Long userId,
            String itemName,
            String type,
            String channel,
            LocalDateTime occurredAt,
            int limit
    ) {
        String query = normalize(itemName);
        if (query.isBlank()) {
            return List.of();
        }
        LocalDateTime now = occurredAt == null ? LocalDateTime.now() : occurredAt;
        List<TransactionResponse> rows = transactionMapper.selectRecords(
                userId, blankToNull(type), now.minusDays(180), now, blankToNull(channel), null, null, null, 300, 0L);
        if (rows.isEmpty()) {
            rows = transactionMapper.selectRecords(userId, blankToNull(type), null, now, blankToNull(channel), null, null, null, 300, 0L);
        }

        Map<String, TemplateCandidate> candidates = new HashMap<>();
        for (TransactionResponse row : rows) {
            if (!hasActiveReferences(userId, row)) {
                continue;
            }
            double textScore = textMatchScore(query, row);
            if (textScore < 25) {
                continue;
            }
            String key = templateKey(row);
            TemplateCandidate candidate = candidates.computeIfAbsent(key, ignored -> new TemplateCandidate(row));
            candidate.addContext(row, now, textScore);
        }

        return candidates.values().stream()
                .filter(TemplateCandidate::contextConfident)
                .sorted(Comparator.comparingDouble(TemplateCandidate::score).reversed())
                .limit(limit)
                .map(TemplateCandidate::toResponse)
                .toList();
    }

    public ExpenseTransaction create(Long userId, TransactionRequest request) {
        ensureOwnedReferences(userId, request);
        validateContext(request);
        ExpenseTransaction transaction = toEntity(new ExpenseTransaction(), userId, request);
        transactionMapper.insert(transaction);
        log.info("新增交易记录 userId={} transactionId={}", userId, transaction.getId());
        return transaction;
    }

    public boolean existsSameTransaction(Long userId, TransactionRequest request) {
        LambdaQueryWrapper<ExpenseTransaction> wrapper = new LambdaQueryWrapper<ExpenseTransaction>()
                .eq(ExpenseTransaction::getUserId, userId)
                .eq(ExpenseTransaction::getType, request.type())
                .eq(ExpenseTransaction::getItemName, request.itemName().trim())
                .eq(ExpenseTransaction::getAmount, request.amount())
                .eq(ExpenseTransaction::getOccurredAt, request.occurredAt())
                .eq(ExpenseTransaction::getChannel, request.channel())
                .eq(ExpenseTransaction::getPaymentMethodId, request.paymentMethodId())
                .eq(ExpenseTransaction::getCategoryId, request.categoryId());
        applyNullableEq(wrapper, ExpenseTransaction::getOnlineApp,
                "ONLINE".equals(request.channel()) ? trimToNull(request.onlineApp()) : null);
        applyNullableEq(wrapper, ExpenseTransaction::getOfflinePlace,
                "OFFLINE".equals(request.channel()) ? trimToNull(request.offlinePlace()) : null);
        applyNullableEq(wrapper, ExpenseTransaction::getNote, trimToNull(request.note()));
        return transactionMapper.selectCount(wrapper) > 0;
    }

    public ExpenseTransaction update(Long userId, Long id, TransactionRequest request) {
        ExpenseTransaction transaction = requireOwned(userId, id);
        ensureOwnedReferences(userId, request);
        validateContext(request);
        toEntity(transaction, userId, request);
        transactionMapper.updateById(transaction);
        log.info("更新交易记录 userId={} transactionId={}", userId, id);
        return transaction;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        transactionMapper.deleteById(id);
        log.info("删除交易记录 userId={} transactionId={}", userId, id);
    }

    private ExpenseTransaction requireOwned(Long userId, Long id) {
        ExpenseTransaction transaction = transactionMapper.selectOne(new LambdaQueryWrapper<ExpenseTransaction>()
                .eq(ExpenseTransaction::getId, id)
                .eq(ExpenseTransaction::getUserId, userId));
        if (transaction == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        return transaction;
    }

    private void ensureOwnedReferences(Long userId, TransactionRequest request) {
        categoryService.requireOwned(userId, request.categoryId());
        paymentMethodService.requireOwned(userId, request.paymentMethodId());
    }

    private void validateContext(TransactionRequest request) {
        // 线上/线下的上下文字段不同，这里统一收口校验，避免前端绕过表单后写入半结构化脏数据。
        if ("OFFLINE".equals(request.channel()) && isBlank(request.offlinePlace())) {
            throw new IllegalArgumentException("线下记录需要填写地点");
        }
        if ("ONLINE".equals(request.channel()) && "EXPENSE".equals(request.type()) && isBlank(request.onlineApp())) {
            throw new IllegalArgumentException("线上支出需要填写消费 APP");
        }
    }

    private ExpenseTransaction toEntity(ExpenseTransaction transaction, Long userId, TransactionRequest request) {
        transaction.setUserId(userId);
        transaction.setType(request.type());
        transaction.setItemName(request.itemName().trim());
        transaction.setAmount(request.amount());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setChannel(request.channel());
        transaction.setOnlineApp("ONLINE".equals(request.channel()) ? trimToNull(request.onlineApp()) : null);
        transaction.setOfflinePlace("OFFLINE".equals(request.channel()) ? trimToNull(request.offlinePlace()) : null);
        PaymentMethod paymentMethod = paymentMethodService.requireOwned(userId, request.paymentMethodId());
        transaction.setPaymentMethodId(paymentMethod.getId());
        transaction.setPaymentMethodName(paymentMethod.getName());
        transaction.setCategoryId(request.categoryId());
        transaction.setNote(trimToNull(request.note()));
        return transaction;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private <T> void applyNullableEq(
            LambdaQueryWrapper<ExpenseTransaction> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<ExpenseTransaction, T> column,
            T value
    ) {
        if (value == null) {
            wrapper.isNull(column);
        } else {
            wrapper.eq(column, value);
        }
    }

    private boolean hasActiveReferences(Long userId, TransactionResponse row) {
        try {
            categoryService.requireOwned(userId, row.getCategoryId());
            paymentMethodService.requireOwned(userId, row.getPaymentMethodId());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String templateKey(TransactionResponse row) {
        return String.join("|",
                normalize(row.getType()),
                normalize(row.getItemName()),
                normalize(row.getChannel()),
                normalize(row.getOnlineApp()),
                normalize(row.getOfflinePlace()),
                String.valueOf(row.getPaymentMethodId()),
                String.valueOf(row.getCategoryId())
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private double textMatchScore(String query, TransactionResponse row) {
        double score = 0;
        String itemName = normalize(row.getItemName());
        String categoryName = normalize(row.getCategoryName());
        String onlineApp = normalize(row.getOnlineApp());
        String offlinePlace = normalize(row.getOfflinePlace());
        String note = normalize(row.getNote());
        if (itemName.equals(query)) {
            score += 130;
        } else if (itemName.startsWith(query) || query.startsWith(itemName)) {
            score += 95;
        } else if (itemName.contains(query) || query.contains(itemName)) {
            score += 80;
        }
        if (categoryName.contains(query)) {
            score += 35;
        }
        if (onlineApp.contains(query) || offlinePlace.contains(query)) {
            score += 30;
        }
        if (note.contains(query)) {
            score += 18;
        }
        return score;
    }

    private static final class TemplateCandidate {
        private final TransactionResponse template;
        private int count;
        private double score;
        private double bestTextScore;
        private int minTimeDeltaMinutes = Integer.MAX_VALUE;
        private boolean sameWeekday;
        private boolean sameDayType;
        private boolean amountChanged;

        private TemplateCandidate(TransactionResponse template) {
            this.template = template;
        }

        private void add(TransactionResponse row, LocalDateTime now) {
            count++;
            int timeDelta = timeDeltaMinutes(now, row.getOccurredAt());
            minTimeDeltaMinutes = Math.min(minTimeDeltaMinutes, timeDelta);
            sameWeekday = sameWeekday || now.getDayOfWeek() == row.getOccurredAt().getDayOfWeek();
            sameDayType = sameDayType || isWeekend(now.getDayOfWeek()) == isWeekend(row.getOccurredAt().getDayOfWeek());
            long days = Math.max(0, ChronoUnit.DAYS.between(row.getOccurredAt().toLocalDate(), now.toLocalDate()));
            double timeScore = Math.max(0, 40 - timeDelta / 6.0);
            double weekdayScore = now.getDayOfWeek() == row.getOccurredAt().getDayOfWeek() ? 16 : 0;
            double dayTypeScore = isWeekend(now.getDayOfWeek()) == isWeekend(row.getOccurredAt().getDayOfWeek()) ? 6 : 0;
            double recencyScore = Math.max(0, 20 - days / 6.0);
            score += timeScore + weekdayScore + dayTypeScore + recencyScore + 8;
            amountChanged = amountChanged || template.getAmount().compareTo(row.getAmount()) != 0;
        }

        private void addContext(TransactionResponse row, LocalDateTime now, double textScore) {
            bestTextScore = Math.max(bestTextScore, textScore);
            add(row, now);
            score += textScore;
        }

        private double score() {
            return score + Math.min(count, 8) * 6;
        }

        private boolean contextConfident() {
            return bestTextScore >= 80 || (bestTextScore >= 35 && score() >= 95);
        }

        private TransactionTemplateResponse toResponse() {
            return new TransactionTemplateResponse(
                    template.getType(),
                    template.getItemName(),
                    template.getAmount(),
                    template.getChannel(),
                    template.getOnlineApp(),
                    template.getOfflinePlace(),
                    template.getPaymentMethodId(),
                    template.getPaymentMethodName(),
                    template.getCategoryId(),
                    template.getCategoryName(),
                    template.getNote(),
                    reason(),
                    Math.round(score() * 10.0) / 10.0
            );
        }

        private String reason() {
            List<String> reasons = new ArrayList<>();
            if (count > 1) {
                reasons.add("历史出现 " + count + " 次");
            }
            if (minTimeDeltaMinutes <= 90) {
                reasons.add("常在当前时段记录");
            }
            if (sameWeekday) {
                reasons.add("同一星期习惯");
            } else if (sameDayType) {
                reasons.add("工作日/周末习惯相近");
            }
            if (amountChanged) {
                reasons.add("金额参考最近记录");
            }
            if (reasons.isEmpty()) {
                return "历史记录模板";
            }
            return String.join("，", reasons);
        }

        private static int timeDeltaMinutes(LocalDateTime now, LocalDateTime occurredAt) {
            int nowMinutes = now.getHour() * 60 + now.getMinute();
            int rowMinutes = occurredAt.getHour() * 60 + occurredAt.getMinute();
            int delta = Math.abs(nowMinutes - rowMinutes);
            return Math.min(delta, 1440 - delta);
        }

        private static boolean isWeekend(DayOfWeek dayOfWeek) {
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        }
    }
}
