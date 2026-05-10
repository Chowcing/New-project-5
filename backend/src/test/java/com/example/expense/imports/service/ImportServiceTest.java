package com.example.expense.imports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.category.service.CategoryService;
import com.example.expense.imports.entity.ImportJob;
import com.example.expense.imports.mapper.ImportJobMapper;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.transaction.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {
    @Mock
    private ImportJobMapper importJobMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private PaymentMethodService paymentMethodService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private ThreadPoolTaskExecutor importTaskExecutor;

    @Test
    void createTransactionsCsvJobPersistsJobAndQueuesWorker() {
        ImportService service = service();
        AtomicReference<ImportJob> insertedJob = new AtomicReference<>();
        when(importJobMapper.selectOne(any())).thenAnswer(invocation -> insertedJob.get());
        when(importJobMapper.insert(any(ImportJob.class))).thenAnswer(invocation -> {
            ImportJob job = invocation.getArgument(0);
            job.setId(99L);
            insertedJob.set(job);
            return 1;
        });

        var response = service.createTransactionsCsvJob(1001L, csvFile());

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalRows()).isZero();
        assertThat(insertedJob.get().getUserId()).isEqualTo(1001L);
        assertThat(insertedJob.get().getOriginalFilename()).isEqualTo("transactions.csv");
        assertThat(insertedJob.get().getCsvContent()).contains("冰棍");
        verify(importTaskExecutor).execute(any(Runnable.class));
    }

    @Test
    void createTransactionsCsvJobReusesRunningJobForSameFile() {
        ImportService service = service();
        ImportJob runningJob = new ImportJob();
        runningJob.setId(88L);
        runningJob.setUserId(1001L);
        runningJob.setOriginalFilename("transactions.csv");
        runningJob.setStatus("RUNNING");
        runningJob.setTotalRows(10);
        runningJob.setImportedRows(3);
        runningJob.setFailedRows(1);
        when(importJobMapper.selectOne(any())).thenReturn(runningJob);

        var response = service.createTransactionsCsvJob(1001L, csvFile());

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.importedRows()).isEqualTo(3);
        verify(importJobMapper, never()).insert(any(ImportJob.class));
        verify(importTaskExecutor, never()).execute(any(Runnable.class));
    }

    private ImportService service() {
        return new ImportService(
                importJobMapper,
                categoryService,
                paymentMethodService,
                transactionService,
                new ObjectMapper(),
                importTaskExecutor
        );
    }

    private MockMultipartFile csvFile() {
        String csv = "类型,事项,金额,发生时间,渠道,线上APP,线下地点,支付方式,分类,备注\n"
                + "支出,冰棍,3.50,2026-05-09 12:00,线下,,便利店,现金,餐饮,\n";
        return new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }
}
