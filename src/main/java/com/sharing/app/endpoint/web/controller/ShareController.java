package com.sharing.app.endpoint.web.controller;

import com.sharing.app.endpoint.web.model.ExpenseItemForm;
import com.sharing.app.endpoint.web.model.ShareForm;
import com.sharing.app.model.CalculationResult;
import com.sharing.app.model.ContributionType;
import com.sharing.app.model.Expense;
import com.sharing.app.model.Participant;
import com.sharing.app.model.SplitType;
import com.sharing.app.service.FileUploadParserService;
import com.sharing.app.service.ParsedShareData;
import com.sharing.app.service.SettlementCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ShareController {

  private final SettlementCalculator settlementCalculator;
  private final FileUploadParserService fileUploadParserService;

  @GetMapping({"/", "/share"})
  public String showSharePage(Model model) {
    ShareForm form = new ShareForm();
    form.setParticipantsInput("ilo, mahefa, tanjona, sanda");
    form.setExpenses(new ArrayList<>());
    // Initial rows
    form.getExpenses()
        .add(
            new ExpenseItemForm(
                "Loyer", "ilo", null, ContributionType.MONETARY, SplitType.EQUAL, ""));
    form.getExpenses()
        .add(
            new ExpenseItemForm(
                "Sakafo", "mahefa", null, ContributionType.MONETARY, SplitType.EQUAL, ""));
    form.getExpenses()
        .add(
            new ExpenseItemForm(
                "Cuisine / Service", "sanda", null, ContributionType.SERVICE, SplitType.EQUAL, ""));

    model.addAttribute("shareForm", form);
    model.addAttribute("hasResult", false);
    return "share-index";
  }

  @GetMapping("/demo")
  public String loadDemo(Model model) {
    ShareForm form = new ShareForm();
    form.setParticipantsInput("ilo, mahefa, tanjona, sanda");

    List<ExpenseItemForm> demoExpenses = new ArrayList<>();
    // Cash expenses
    demoExpenses.add(
        new ExpenseItemForm(
            "Loyer (Rent)",
            "ilo",
            new BigDecimal("1400.00"),
            ContributionType.MONETARY,
            SplitType.EQUAL,
            "ilo, mahefa, tanjona, sanda"));
    demoExpenses.add(
        new ExpenseItemForm(
            "Sakafo (Groceries)",
            "mahefa",
            new BigDecimal("200.00"),
            ContributionType.MONETARY,
            SplitType.EQUAL,
            "ilo, mahefa, tanjona, sanda"));

    // Services / Income Compensation
    demoExpenses.add(
        new ExpenseItemForm(
            "Preparation Sakafo (Cooking Service)",
            "sanda",
            new BigDecimal("120.00"),
            ContributionType.SERVICE,
            SplitType.EQUAL,
            "ilo, mahefa, tanjona, sanda"));
    demoExpenses.add(
        new ExpenseItemForm(
            "Entretien & Reparations (Maintenance)",
            "tanjona",
            new BigDecimal("80.00"),
            ContributionType.SERVICE,
            SplitType.EQUAL,
            "ilo, mahefa, tanjona, sanda"));

    form.setExpenses(demoExpenses);
    return processCalculation(form, model);
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String uploadFileSynchronously(@RequestParam("file") MultipartFile file, Model model) {
    ShareForm fallbackForm = new ShareForm();
    model.addAttribute("shareForm", fallbackForm);

    if (file == null || file.isEmpty()) {
      model.addAttribute("errorMessage", "Please select a non-empty CSV or JSON file to upload.");
      model.addAttribute("hasResult", false);
      return "share-index";
    }

    try {
      ParsedShareData parsed =
          fileUploadParserService.parse(file.getInputStream(), file.getOriginalFilename());

      if (parsed.participants().size() < 2) {
        model.addAttribute(
            "errorMessage", "The uploaded file must contain at least 2 participants.");
        model.addAttribute("hasResult", false);
        return "share-index";
      }

      CalculationResult result =
          settlementCalculator.calculateWithParticipants(parsed.participants(), parsed.expenses());

      // Pre-fill form from uploaded data for interactivity
      ShareForm form = new ShareForm();
      String participantsStr =
          String.join(
              ", ",
              parsed.participants().stream()
                  .map(
                      p ->
                          p.declaredIncome() != null
                              ? p.name() + " (" + p.declaredIncome() + "€)"
                              : p.name())
                  .toList());
      form.setParticipantsInput(participantsStr);

      List<ExpenseItemForm> itemForms = new ArrayList<>();
      for (Expense e : parsed.expenses()) {
        String sharedWithStr = String.join(", ", e.sharedWith());
        itemForms.add(
            new ExpenseItemForm(
                e.description(),
                e.paidBy(),
                e.amount(),
                e.contributionType(),
                e.splitType(),
                sharedWithStr));
      }
      form.setExpenses(itemForms);

      model.addAttribute("shareForm", form);
      model.addAttribute("result", result);
      model.addAttribute("hasResult", true);
      model.addAttribute(
          "participantList", parsed.participants().stream().map(Participant::name).toList());
      model.addAttribute(
          "successMessage",
          String.format(
              "File '%s' processed synchronously: %d member(s), %d item(s) calculated.",
              file.getOriginalFilename(), parsed.participants().size(), parsed.expenses().size()));
    } catch (Exception e) {
      log.warn("Upload processing error: {}", e.getMessage(), e);
      model.addAttribute("errorMessage", "Failed to process file: " + e.getMessage());
      model.addAttribute("hasResult", false);
    }

    return "share-index";
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
                  item.getContributionType() != null
                      ? item.getContributionType()
                      : ContributionType.MONETARY,
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
      log.warn("Calculation error: {}", e.getMessage(), e);
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
        .map(p -> p.replaceAll("\\(.*\\)", "").trim()) // clean optional (income)
        .filter(s -> !s.isEmpty())
        .distinct()
        .toList();
  }
}
