package com.sharing.app.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record Expense(
    String id,
    String description,
    String paidBy,
    BigDecimal amount,
    ContributionType contributionType,
    SplitType splitType,
    List<ExpenseSplit> splits,
    List<String> sharedWith) {

  public Expense {
    if (contributionType == null) {
      contributionType = ContributionType.MONETARY;
    }
    if (splitType == null) {
      splitType = SplitType.EQUAL;
    }
    if (splits == null) {
      splits = Collections.emptyList();
    }
    if (sharedWith == null) {
      sharedWith = Collections.emptyList();
    }
  }

  public Expense(
      String id,
      String description,
      String paidBy,
      BigDecimal amount,
      SplitType splitType,
      List<ExpenseSplit> splits,
      List<String> sharedWith) {
    this(id, description, paidBy, amount, ContributionType.MONETARY, splitType, splits, sharedWith);
  }

  public boolean isService() {
    return contributionType == ContributionType.SERVICE;
  }
}
