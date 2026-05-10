package com.example.expense.imports.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ImportJobResumeRunner {
    private final ImportService importService;

    public ImportJobResumeRunner(ImportService importService) {
        this.importService = importService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinishedImportJobs() {
        importService.resumeUnfinishedJobs();
    }
}
