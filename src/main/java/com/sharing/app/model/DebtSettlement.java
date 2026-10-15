package com.sharing.app.model;

import java.math.BigDecimal;

public record DebtSettlement(String debtor, String creditor, BigDecimal amount) {}
