package com.sharing.app.model;

import java.math.BigDecimal;
import java.util.List;

public record CalculationResult(
    BigDecimal totalExpenses,
    int participantCount,
    List<ParticipantBalance> balances,
    List<DebtSettlement> settlements,
    List<String> auditLogs) {}
