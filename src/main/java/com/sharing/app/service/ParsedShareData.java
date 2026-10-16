package com.sharing.app.service;

import com.sharing.app.model.Expense;
import com.sharing.app.model.Participant;
import java.util.List;

public record ParsedShareData(List<Participant> participants, List<Expense> expenses) {}
