package com.sharing.app.model;

import java.math.BigDecimal;

public record Participant(String name, BigDecimal declaredIncome) {
  public Participant {
    if (name != null) {
      name = name.trim();
    }
  }

  public static Participant of(String name) {
    return new Participant(name, null);
  }

  public static Participant of(String name, BigDecimal income) {
    return new Participant(name, income);
  }
}
