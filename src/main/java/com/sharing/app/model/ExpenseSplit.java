package com.sharing.app.model;

import java.math.BigDecimal;

public record ExpenseSplit(String participant, BigDecimal amount, BigDecimal percentage) {
  public static ExpenseSplit ofAmount(String participant, BigDecimal amount) {
    return new ExpenseSplit(participant, amount, null);
  }

  public static ExpenseSplit ofPercentage(String participant, BigDecimal percentage) {
    return new ExpenseSplit(participant, null, percentage);
  }

  public static ExpenseSplit ofEqual(String participant) {
    return new ExpenseSplit(participant, null, null);
  }
}
