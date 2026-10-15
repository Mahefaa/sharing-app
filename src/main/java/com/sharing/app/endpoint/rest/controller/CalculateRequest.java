package com.sharing.app.endpoint.rest.controller;

import com.sharing.app.model.Expense;
import java.util.List;

public record CalculateRequest(List<String> participants, List<Expense> expenses) {}
