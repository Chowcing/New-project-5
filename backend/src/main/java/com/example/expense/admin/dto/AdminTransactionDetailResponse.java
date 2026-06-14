package com.example.expense.admin.dto;

import com.example.expense.transaction.dto.TransactionImageResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminTransactionDetailResponse {
    private AdminTransactionResponse transaction;
    private AdminUserResponse user;
    private AdminUserStatisticsResponse statistics;
    private List<TransactionImageResponse> images = new ArrayList<>();
    private List<AdminAuditLogResponse> relatedAuditLogs = new ArrayList<>();

    public void setImages(List<TransactionImageResponse> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }

    public void setRelatedAuditLogs(List<AdminAuditLogResponse> relatedAuditLogs) {
        this.relatedAuditLogs = relatedAuditLogs == null ? new ArrayList<>() : relatedAuditLogs;
    }
}
