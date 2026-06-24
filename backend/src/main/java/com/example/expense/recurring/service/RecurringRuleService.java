package com.example.expense.recurring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.recurring.dto.RecurringRuleRequest;
import com.example.expense.recurring.entity.RecurringRule;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.service.TransactionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecurringRuleService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PAUSED = "PAUSED";
    private static final String RUN_PENDING = "PENDING";
    private static final String RUN_PROCESSING = "PROCESSING";
    private static final String RUN_GENERATED = "GENERATED";
    private static final String RUN_SKIPPED = "SKIPPED";
    private static final String RUN_CANCELLED = "CANCELLED";
    private static final String RUN_FAILED = "FAILED";
    private static final List<String> ACTIONABLE_RUN_STATUSES = List.of(RUN_PENDING, RUN_FAILED);

    private final RecurringRuleMapper recurringRuleMapper;
    private final RecurringRuleRunMapper recurringRuleRunMapper;
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;
    private final TransactionService transactionService;
    private final RecurringRunFailureRecorder failureRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final BusinessAuditLogService businessAuditLogService;

    @Autowired
    public RecurringRuleService(
            RecurringRuleMapper recurringRuleMapper,
            RecurringRuleRunMapper recurringRuleRunMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            TransactionService transactionService,
            RecurringRunFailureRecorder failureRecorder,
            TransactionTemplate transactionTemplate,
            Clock clock,
            BusinessAuditLogService businessAuditLogService
    ) {
        this.recurringRuleMapper = recurringRuleMapper;
        this.recurringRuleRunMapper = recurringRuleRunMapper;
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
        this.transactionService = transactionService;
        this.failureRecorder = failureRecorder;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.businessAuditLogService = businessAuditLogService;
    }

    public List<RecurringRule> listRules(Long userId) {
        return recurringRuleMapper.selectList(new LambdaQueryWrapper<RecurringRule>()
                .eq(RecurringRule::getUserId, userId)
                .eq(RecurringRule::getDeleted, 0)
                .orderByAsc(RecurringRule::getStatus)
                .orderByAsc(RecurringRule::getNextRunDate)
                .orderByDesc(RecurringRule::getId));
    }

    public RecurringRule getRule(Long userId, Long id) {
        return requireOwned(userId, id);
    }

    @Transactional
    public RecurringRule createRule(Long userId, RecurringRuleRequest request) {
        RecurringRule rule = new RecurringRule();
        applyRequest(rule, userId, request);
        rule.setStatus(normalizeStatus(request.status()));
        validateRule(rule);
        rule.setNextRunDate(RecurringScheduleCalculator.calculateInitialNextRunDate(rule, LocalDate.now(clock)));
        recurringRuleMapper.insert(rule);
        audit(userId, "RECURRING_RULE_CREATE", "RECURRING_RULE", rule.getId());
        return rule;
    }

    @Transactional
    public RecurringRule updateRule(Long userId, Long id, RecurringRuleRequest request) {
        RecurringRule rule = requireOwned(userId, id);
        RecurringRuleRun actionableRun = findActionableRun(userId, id);
        applyRequest(rule, userId, request);
        rule.setStatus(normalizeStatus(request.status()));
        validateRule(rule);
        rule.setNextRunDate(RecurringScheduleCalculator.calculateInitialNextRunDate(rule, LocalDate.now(clock)));
        recurringRuleMapper.updateById(rule);
        if (STATUS_PAUSED.equals(rule.getStatus())) {
            if (actionableRun != null) {
                cancelActionableRuns(userId, id, "规则已暂停");
                advanceRule(rule, rule.getNextRunDate());
            }
        } else {
            syncActionableRunSnapshot(rule, actionableRun);
        }
        audit(userId, "RECURRING_RULE_UPDATE", "RECURRING_RULE", id);
        return rule;
    }

    @Transactional
    public void deleteRule(Long userId, Long id) {
        requireOwned(userId, id);
        recurringRuleMapper.deleteById(id);
        cancelActionableRuns(userId, id, "规则已删除");
        audit(userId, "RECURRING_RULE_DELETE", "RECURRING_RULE", id);
    }

    public List<RecurringRuleRun> listRuns(Long userId, Long ruleId) {
        requireOwned(userId, ruleId);
        return recurringRuleRunMapper.selectList(new LambdaQueryWrapper<RecurringRuleRun>()
                .eq(RecurringRuleRun::getUserId, userId)
                .eq(RecurringRuleRun::getRuleId, ruleId)
                .orderByDesc(RecurringRuleRun::getDueDate)
                .orderByDesc(RecurringRuleRun::getId));
    }

    public List<RecurringRuleRun> listDueRuns(Long userId, LocalDate date) {
        seedDueRunsForUser(userId, date);
        return recurringRuleRunMapper.selectList(new LambdaQueryWrapper<RecurringRuleRun>()
                .eq(RecurringRuleRun::getUserId, userId)
                .in(RecurringRuleRun::getStatus, ACTIONABLE_RUN_STATUSES)
                .orderByAsc(RecurringRuleRun::getDueDate)
                .orderByAsc(RecurringRuleRun::getId))
                .stream()
                .filter(run -> !RecurringScheduleCalculator.calculateAttentionDate(run.getDueDate(), run.getReminderDaysBefore()).isAfter(date))
                .sorted(Comparator
                        .comparing((RecurringRuleRun run) -> RecurringScheduleCalculator.calculateAttentionDate(run.getDueDate(), run.getReminderDaysBefore()))
                        .thenComparing(RecurringRuleRun::getDueDate)
                        .thenComparing(RecurringRuleRun::getId))
                .toList();
    }

    public void seedDueRunsForAllUsers(LocalDate date) {
        List<RecurringRule> rules = recurringRuleMapper.selectList(new LambdaQueryWrapper<RecurringRule>()
                .eq(RecurringRule::getDeleted, 0)
                .eq(RecurringRule::getStatus, STATUS_ACTIVE)
                .isNotNull(RecurringRule::getNextRunDate));
        for (RecurringRule rule : rules) {
            seedDueRun(rule, date);
        }
    }

    public void seedDueRunsForUser(Long userId, LocalDate date) {
        List<RecurringRule> rules = recurringRuleMapper.selectList(new LambdaQueryWrapper<RecurringRule>()
                .eq(RecurringRule::getUserId, userId)
                .eq(RecurringRule::getDeleted, 0)
                .eq(RecurringRule::getStatus, STATUS_ACTIVE)
                .isNotNull(RecurringRule::getNextRunDate));
        for (RecurringRule rule : rules) {
            seedDueRun(rule, date);
        }
    }

    public RecurringRuleRun generateRun(Long userId, Long runId) {
        RecurringRuleRun run = claimActionableRun(userId, runId);
        try {
            return transactionTemplate.execute(status -> completeGenerateRun(userId, run));
        } catch (RuntimeException ex) {
            failureRecorder.recordFailure(run, trimToNull(ex.getMessage()));
            throw ex;
        }
    }

    private RecurringRuleRun completeGenerateRun(Long userId, RecurringRuleRun run) {
        RecurringRule rule = requireOwned(userId, run.getRuleId());
        TransactionRequest request = toTransactionRequest(run);
        ExpenseTransaction transaction = transactionService.create(userId, request);
        run.setStatus(RUN_GENERATED);
        run.setTransactionId(transaction.getId());
        run.setErrorMessage(null);
        run.setProcessedAt(LocalDateTime.now(clock));
        recurringRuleRunMapper.updateById(run);
        advanceRule(rule, run.getDueDate());
        audit(userId, "RECURRING_RUN_GENERATE", "RECURRING_RUN", run.getId());
        return run;
    }

    @Transactional
    public RecurringRuleRun skipRun(Long userId, Long runId) {
        RecurringRuleRun run = claimActionableRun(userId, runId);
        RecurringRule rule = requireOwned(userId, run.getRuleId());
        run.setStatus(RUN_SKIPPED);
        run.setErrorMessage(null);
        run.setProcessedAt(LocalDateTime.now(clock));
        recurringRuleRunMapper.updateById(run);
        advanceRule(rule, run.getDueDate());
        return run;
    }

    private void applyRequest(RecurringRule rule, Long userId, RecurringRuleRequest request) {
        rule.setUserId(userId);
        rule.setName(trimToNull(request.name()));
        rule.setType(normalizeType(request.type()));
        rule.setItemName(trimToNull(request.itemName()));
        rule.setAmount(request.amount());
        rule.setChannel(normalizeChannel(request.channel()));
        rule.setOnlineApp("ONLINE".equals(rule.getChannel()) ? trimToNull(request.onlineApp()) : null);
        rule.setOfflinePlace("OFFLINE".equals(rule.getChannel()) ? trimToNull(request.offlinePlace()) : null);

        Category category = categoryService.requireOwned(userId, request.categoryId());
        PaymentMethod paymentMethod = paymentMethodService.requireOwned(userId, request.paymentMethodId());
        rule.setCategoryId(category.getId());
        rule.setCategoryName(category.getName());
        rule.setPaymentMethodId(paymentMethod.getId());
        rule.setPaymentMethodName(paymentMethod.getName());
        rule.setNote(trimToNull(request.note()));

        rule.setScheduleType(normalizeScheduleType(request.scheduleType()));
        rule.setIntervalValue(request.intervalValue());
        rule.setDayOfMonth(request.dayOfMonth());
        rule.setWeekday(RecurringScheduleCalculator.normalizeWeekday(request.weekday()));
        rule.setStartDate(request.startDate());
        rule.setEndDate(request.endDate());
        rule.setReminderDaysBefore(request.reminderDaysBefore() == null ? 0 : request.reminderDaysBefore());
    }

    private void validateRule(RecurringRule rule) {
        validateContext(rule);
        if ("MONTHLY".equals(rule.getScheduleType()) && rule.getDayOfMonth() == null) {
            throw new IllegalArgumentException("月度周期需要选择日期");
        }
        if ("WEEKLY".equals(rule.getScheduleType()) && rule.getWeekday() == null) {
            throw new IllegalArgumentException("周度周期需要选择星期");
        }
    }

    private void audit(Long userId, String action, String targetType, Long targetId) {
        businessAuditLogService.recordSuccess(userId, action, targetType, targetId, "RECURRING");
    }

    private void validateContext(RecurringRule rule) {
        if ("OFFLINE".equals(rule.getChannel()) && trimToNull(rule.getOfflinePlace()) == null) {
            throw new IllegalArgumentException("线下周期记录需要填写地点");
        }
        if ("ONLINE".equals(rule.getChannel()) && "EXPENSE".equals(rule.getType()) && trimToNull(rule.getOnlineApp()) == null) {
            throw new IllegalArgumentException("线上支出周期记录需要填写消费 APP");
        }
    }

    private void seedDueRun(RecurringRule rule, LocalDate date) {
        LocalDate nextRunDate = rule.getNextRunDate();
        if (nextRunDate == null) {
            return;
        }
        LocalDate attentionDate = RecurringScheduleCalculator.calculateAttentionDate(nextRunDate, rule.getReminderDaysBefore());
        if (attentionDate.isAfter(date)) {
            return;
        }
        Long count = recurringRuleRunMapper.selectCount(new LambdaQueryWrapper<RecurringRuleRun>()
                .eq(RecurringRuleRun::getRuleId, rule.getId())
                .eq(RecurringRuleRun::getDueDate, nextRunDate));
        if (count != null && count > 0) {
            return;
        }

        RecurringRuleRun run = new RecurringRuleRun();
        copyRuleSnapshot(rule, run);
        run.setUserId(rule.getUserId());
        run.setRuleId(rule.getId());
        run.setRuleName(rule.getName());
        run.setDueDate(nextRunDate);
        run.setReminderDaysBefore(rule.getReminderDaysBefore());
        run.setStatus(RUN_PENDING);
        try {
            recurringRuleRunMapper.insert(run);
        } catch (DuplicateKeyException ignored) {
            // 并发触发首页查询或定时任务时，重复创建相同周期实例属于正常竞争，忽略即可。
        }
    }

    private void copyRuleSnapshot(RecurringRule rule, RecurringRuleRun run) {
        run.setType(rule.getType());
        run.setItemName(rule.getItemName());
        run.setAmount(rule.getAmount());
        run.setChannel(rule.getChannel());
        run.setOnlineApp(rule.getOnlineApp());
        run.setOfflinePlace(rule.getOfflinePlace());
        run.setPaymentMethodId(rule.getPaymentMethodId());
        run.setCategoryId(rule.getCategoryId());
        run.setNote(rule.getNote());
    }

    private void syncActionableRunSnapshot(RecurringRule rule, RecurringRuleRun actionableRun) {
        if (actionableRun == null) {
            return;
        }

        if (rule.getNextRunDate() == null) {
            actionableRun.setStatus(RUN_CANCELLED);
            actionableRun.setErrorMessage("规则已结束");
            actionableRun.setProcessedAt(LocalDateTime.now(clock));
            recurringRuleRunMapper.updateById(actionableRun);
            return;
        }

        copyRuleSnapshot(rule, actionableRun);
        actionableRun.setRuleName(rule.getName());
        actionableRun.setReminderDaysBefore(rule.getReminderDaysBefore());
        actionableRun.setDueDate(rule.getNextRunDate());
        actionableRun.setErrorMessage(null);
        recurringRuleRunMapper.updateById(actionableRun);
    }

    private RecurringRuleRun findActionableRun(Long userId, Long ruleId) {
        return recurringRuleRunMapper.selectOne(new LambdaQueryWrapper<RecurringRuleRun>()
                .eq(RecurringRuleRun::getUserId, userId)
                .eq(RecurringRuleRun::getRuleId, ruleId)
                .in(RecurringRuleRun::getStatus, ACTIONABLE_RUN_STATUSES)
                .orderByAsc(RecurringRuleRun::getDueDate)
                .orderByAsc(RecurringRuleRun::getId)
                .last("LIMIT 1"));
    }

    private void cancelActionableRuns(Long userId, Long ruleId, String message) {
        List<RecurringRuleRun> actionableRuns = recurringRuleRunMapper.selectList(new LambdaQueryWrapper<RecurringRuleRun>()
                .eq(RecurringRuleRun::getUserId, userId)
                .eq(RecurringRuleRun::getRuleId, ruleId)
                .in(RecurringRuleRun::getStatus, ACTIONABLE_RUN_STATUSES));
        for (RecurringRuleRun actionableRun : actionableRuns) {
            actionableRun.setStatus(RUN_CANCELLED);
            actionableRun.setErrorMessage(message);
            actionableRun.setProcessedAt(LocalDateTime.now(clock));
            recurringRuleRunMapper.updateById(actionableRun);
        }
    }

    private void advanceRule(RecurringRule rule, LocalDate dueDate) {
        LocalDate nextRunDate = RecurringScheduleCalculator.calculateNextRunDateAfter(rule, dueDate);
        rule.setNextRunDate(nextRunDate);
        recurringRuleMapper.updateById(rule);
    }

    private RecurringRule requireOwned(Long userId, Long id) {
        RecurringRule rule = recurringRuleMapper.selectOne(new LambdaQueryWrapper<RecurringRule>()
                .eq(RecurringRule::getId, id)
                .eq(RecurringRule::getUserId, userId)
                .eq(RecurringRule::getDeleted, 0));
        if (rule == null) {
            throw new IllegalArgumentException("周期规则不存在");
        }
        return rule;
    }

    private RecurringRuleRun claimActionableRun(Long userId, Long id) {
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = recurringRuleRunMapper.update(null, new UpdateWrapper<RecurringRuleRun>()
                .eq("id", id)
                .eq("user_id", userId)
                .in("status", ACTIONABLE_RUN_STATUSES)
                .set("status", RUN_PROCESSING)
                .set("error_message", null)
                .set("processed_at", now));
        RecurringRuleRun run = recurringRuleRunMapper.selectOne(new LambdaQueryWrapper<RecurringRuleRun>()
                .eq(RecurringRuleRun::getId, id)
                .eq(RecurringRuleRun::getUserId, userId));
        if (run == null) {
            throw new IllegalArgumentException("待处理周期记录不存在");
        }
        if (updated != 1) {
            throw new IllegalArgumentException("该周期记录已处理");
        }
        return run;
    }

    private TransactionRequest toTransactionRequest(RecurringRuleRun run) {
        return new TransactionRequest(
                run.getType(),
                run.getItemName(),
                run.getAmount(),
                run.getDueDate().atStartOfDay(),
                run.getChannel(),
                run.getOnlineApp(),
                null,
                run.getOfflinePlace(),
                run.getPaymentMethodId(),
                run.getCategoryId(),
                run.getNote()
        );
    }

    private String normalizeType(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeChannel(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeScheduleType(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
