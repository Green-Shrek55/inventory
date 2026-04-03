package com.kursach.inventory.web.dto;

import com.kursach.inventory.domain.MaintenanceStatus;
import jakarta.validation.constraints.NotNull;

public class MaintenanceStatusForm {
    @NotNull
    private MaintenanceStatus status;
    private String resolutionNote;
    private Long assigneeId;

    public MaintenanceStatus getStatus() {
        return status;
    }

    public void setStatus(MaintenanceStatus status) {
        this.status = status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }
}
