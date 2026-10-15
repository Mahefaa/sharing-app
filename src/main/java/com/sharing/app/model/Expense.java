package com.sharing.app.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record Expense(
    String id,
    String description,
    String paidBy,
    BigDecimal amount,
    SplitType splitType,
    List<ExpenseSplit> splits,
    List<String> sharedWith) {
  public Expense {
    if (splits == null) {
      splits = Collections.emptyList();
    }
    if (sharedWith == null) {
      sharedWith = Collections.emptyList();
    }
  }
}
