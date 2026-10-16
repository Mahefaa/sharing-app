package com.sharing.app.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record CalculationResult(
    BigDecimal totalExpenses,
    BigDecimal totalMonetaryExpenses,
    BigDecimal totalServicesValue,
    int participantCount,
    List<ParticipantBalance> balances,
    List<DebtSettlement> settlements,
    List<String> auditLogs) {

  public CalculationResult(
      BigDecimal totalExpenses,
      int participantCount,
      List<ParticipantBalance> balances,
      List<DebtSettlement> settlements,
      List<String> auditLogs) {
    this(
        totalExpenses,
        totalExpenses,
        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
        participantCount,
        balances,
        settlements,
        auditLogs);
  }
}
