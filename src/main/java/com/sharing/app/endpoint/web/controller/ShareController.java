package com.sharing.app.endpoint.web.controller;

import com.sharing.app.endpoint.web.model.ExpenseItemForm;
import com.sharing.app.endpoint.web.model.ShareForm;
import com.sharing.app.model.CalculationResult;
import com.sharing.app.model.Expense;
import com.sharing.app.model.SplitType;
import com.sharing.app.service.SettlementCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ShareController {

  private final SettlementCalculator settlementCalculator;

  @GetMapping({"/", "/share"})
  public String showSharePage(Model model) {
    ShareForm form = new ShareForm();
    form.setParticipantsInput("Alice, Bob, Charlie, David");
    form.setExpenses(new ArrayList<>());
    // Add 2 empty expense rows by default
    form.getExpenses().add(new ExpenseItemForm("", "Alice", null, SplitType.EQUAL, ""));
    form.getExpenses().add(new ExpenseItemForm("", "Bob", null, SplitType.EQUAL, ""));

    model.addAttribute("shareForm", form);
    model.addAttribute("hasResult", false);
    return "share-index";
  }

  @GetMapping("/demo")
  public String loadDemo(Model model) {
    ShareForm form = new ShareForm();
    form.setParticipantsInput("Alice, Bob, Charlie, David");

    List<ExpenseItemForm> demoExpenses = new ArrayList<>();
    demoExpenses.add(
        new ExpenseItemForm(
            "Villa Rental",
            "Alice",
            new BigDecimal("400.00"),
            SplitType.EQUAL,
            "Alice, Bob, Charlie, David"));
    demoExpenses.add(
        new ExpenseItemForm(
            "Groceries & BBQ",
            "Bob",
            new BigDecimal("120.00"),
            SplitType.EQUAL,
            "Alice, Bob, Charlie, David"));
    demoExpenses.add(
        new ExpenseItemForm(
            "Rental Car Fuel",
            "Charlie",
            new BigDecimal("80.00"),
            SplitType.EQUAL,
            "Bob, Charlie, David"));
    demoExpenses.add(
        new ExpenseItemForm(
            "Museum Tickets",
            "David",
            new BigDecimal("60.00"),
            SplitType.EQUAL,
            "Alice, Charlie, David"));

    form.setExpenses(demoExpenses);
    return processCalculation(form, model);
  }

  @PostMapping("/calculate")
  public String calculateSettlement(@ModelAttribute("shareForm") ShareForm form, Model model) {
    return processCalculation(form, model);
  }

  private String processCalculation(ShareForm form, Model model) {
    model.addAttribute("shareForm", form);

    try {
      List<String> participants = parseParticipants(form.getParticipantsInput());
      if (participants.size() < 2) {
        model.addAttribute(
            "errorMessage", "Please provide at least 2 participants (comma or newline separated).");
        model.addAttribute("hasResult", false);
        return "share-index";
      }

      List<Expense> expenses = new ArrayList<>();
      if (form.getExpenses() != null) {
        for (ExpenseItemForm item : form.getExpenses()) {
          if (item == null
              || item.getAmount() == null
              || item.getAmount().compareTo(BigDecimal.ZERO) <= 0
              || item.getPaidBy() == null
              || item.getPaidBy().trim().isEmpty()) {
            continue;
          }

          List<String> sharedWith =
              (item.getSharedWithInput() == null || item.getSharedWithInput().trim().isEmpty())
                  ? participants
                  : Arrays.stream(item.getSharedWithInput().split("[,;\n]"))
                      .map(String::trim)
                      .filter(s -> !s.isEmpty())
                      .toList();

          expenses.add(
              new Expense(
                  UUID.randomUUID().toString(),
                  item.getDescription() == null || item.getDescription().isBlank()
                      ? "Expense"
                      : item.getDescription().trim(),
                  item.getPaidBy().trim(),
                  item.getAmount(),
                  item.getSplitType() != null ? item.getSplitType() : SplitType.EQUAL,
                  List.of(),
                  sharedWith));
        }
      }

      CalculationResult result = settlementCalculator.calculate(participants, expenses);
      model.addAttribute("result", result);
      model.addAttribute("hasResult", true);
      model.addAttribute("participantList", participants);
    } catch (Exception e) {
      log.warn("Calculation error: {}", e.getMessage());
      model.addAttribute("errorMessage", e.getMessage());
      model.addAttribute("hasResult", false);
    }

    return "share-index";
  }

  private List<String> parseParticipants(String input) {
    if (input == null || input.trim().isEmpty()) {
      return List.of();
    }
    return Arrays.stream(input.split("[,;\n]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .distinct()
        .toList();
  }
}
