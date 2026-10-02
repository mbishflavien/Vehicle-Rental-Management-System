package com.vrms.dto;

import com.vrms.model.ContractStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull(message = "Status is required") ContractStatus status) {
}
