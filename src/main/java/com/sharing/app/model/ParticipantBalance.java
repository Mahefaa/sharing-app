package com.sharing.app.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ParticipantBalance(
    String participant,
    BigDecimal monetaryPaid,
    BigDecimal serviceValueProvided,
    BigDecimal totalPaid,
    BigDecimal totalShare,
    BigDecimal netBalance,
    BigDecimal declaredIncome) {

  public ParticipantBalance(
      String participant, BigDecimal totalPaid, BigDecimal totalShare, BigDecimal netBalance) {
    this(
        participant,
        totalPaid,
        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
        totalPaid,
        totalShare,
        netBalance,
        null);
  }

  public boolean isCreditor() {
    return netBalance.compareTo(BigDecimal.ZERO) > 0;
  }

  public boolean isDebtor() {
    return netBalance.compareTo(BigDecimal.ZERO) < 0;
  }

  public boolean isSettled() {
    return netBalance.compareTo(BigDecimal.ZERO) == 0;
  }

  public boolean hasServiceContribution() {
    return serviceValueProvided != null && serviceValueProvided.compareTo(BigDecimal.ZERO) > 0;
  }
}
