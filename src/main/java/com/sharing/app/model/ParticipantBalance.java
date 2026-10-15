package com.sharing.app.model;

import java.math.BigDecimal;

public record ParticipantBalance(
    String participant, BigDecimal totalPaid, BigDecimal totalShare, BigDecimal netBalance) {
  public boolean isCreditor() {
    return netBalance.compareTo(BigDecimal.ZERO) > 0;
  }

  public boolean isDebtor() {
    return netBalance.compareTo(BigDecimal.ZERO) < 0;
  }

  public boolean isSettled() {
    return netBalance.compareTo(BigDecimal.ZERO) == 0;
  }
}
