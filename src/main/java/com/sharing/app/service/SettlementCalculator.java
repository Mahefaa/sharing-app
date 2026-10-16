package com.sharing.app.service;

import com.sharing.app.model.CalculationResult;
import com.sharing.app.model.ContributionType;
import com.sharing.app.model.DebtSettlement;
import com.sharing.app.model.Expense;
import com.sharing.app.model.ExpenseSplit;
import com.sharing.app.model.Participant;
import com.sharing.app.model.ParticipantBalance;
import com.sharing.app.model.SplitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SettlementCalculator {

  private static final BigDecimal EPSILON = new BigDecimal("0.005");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  public CalculationResult calculate(List<String> rawParticipants, List<Expense> expenses) {
    List<Participant> participants =
        rawParticipants == null
            ? List.of()
            : rawParticipants.stream()
                .filter(p -> p != null && !p.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .map(Participant::of)
                .toList();

    return calculateWithParticipants(participants, expenses);
  }

  public CalculationResult calculateWithParticipants(
      List<Participant> rawParticipants, List<Expense> expenses) {

    List<Participant> participants =
        rawParticipants == null
            ? List.of()
            : rawParticipants.stream()
                .filter(p -> p != null && p.name() != null && !p.name().trim().isEmpty())
                .distinct()
                .toList();

    List<String> auditLogs = new ArrayList<>();
    if (participants.isEmpty()) {
      return new CalculationResult(
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          0,
          Collections.emptyList(),
          Collections.emptyList(),
          List.of("No participants provided."));
    }

    List<String> participantNames = participants.stream().map(Participant::name).toList();
    Set<String> participantSet = new HashSet<>(participantNames);
    Map<String, BigDecimal> incomeMap = new HashMap<>();
    participants.forEach(
        p -> {
          if (p.declaredIncome() != null) {
            incomeMap.put(p.name(), p.declaredIncome().setScale(2, RoundingMode.HALF_UP));
          }
        });

    Map<String, BigDecimal> monetaryPaidMap = new HashMap<>();
    Map<String, BigDecimal> serviceProvidedMap = new HashMap<>();
    Map<String, BigDecimal> totalShareMap = new HashMap<>();

    participantNames.forEach(
        name -> {
          monetaryPaidMap.put(name, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
          serviceProvidedMap.put(name, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
          totalShareMap.put(name, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        });

    BigDecimal totalMonetary = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalServices = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    if (expenses != null) {
      for (Expense expense : expenses) {
        if (expense == null
            || expense.amount() == null
            || expense.amount().compareTo(BigDecimal.ZERO) <= 0) {
          continue;
        }

        String contributor = expense.paidBy() != null ? expense.paidBy().trim() : "";
        if (!participantSet.contains(contributor)) {
          throw new IllegalArgumentException(
              "Expense contributor '" + contributor + "' is not in the participants list");
        }

        BigDecimal amount = expense.amount().setScale(2, RoundingMode.HALF_UP);
        boolean isService = expense.contributionType() == ContributionType.SERVICE;

        if (isService) {
          totalServices = totalServices.add(amount);
          serviceProvidedMap.put(contributor, serviceProvidedMap.get(contributor).add(amount));
        } else {
          totalMonetary = totalMonetary.add(amount);
          monetaryPaidMap.put(contributor, monetaryPaidMap.get(contributor).add(amount));
        }

        Map<String, BigDecimal> expenseShares =
            computeShares(expense, amount, participantNames, participantSet, incomeMap);
        expenseShares.forEach(
            (participant, share) ->
                totalShareMap.put(participant, totalShareMap.get(participant).add(share)));

        auditLogs.add(
            String.format(
                "%s '%s' of %s EUR contributed by %s (Split: %s across %d member(s))",
                isService ? "Service Contribution" : "Monetary Expense",
                expense.description(),
                amount,
                contributor,
                expense.splitType(),
                expenseShares.size()));
      }
    }

    BigDecimal totalExpenses = totalMonetary.add(totalServices).setScale(2, RoundingMode.HALF_UP);

    List<ParticipantBalance> balances = new ArrayList<>();
    for (Participant p : participants) {
      String name = p.name();
      BigDecimal monetaryPaid = monetaryPaidMap.get(name);
      BigDecimal serviceValue = serviceProvidedMap.get(name);
      BigDecimal totalPaid = monetaryPaid.add(serviceValue).setScale(2, RoundingMode.HALF_UP);
      BigDecimal share = totalShareMap.get(name);
      BigDecimal net = totalPaid.subtract(share).setScale(2, RoundingMode.HALF_UP);
      BigDecimal income = incomeMap.get(name);

      balances.add(
          new ParticipantBalance(name, monetaryPaid, serviceValue, totalPaid, share, net, income));
    }

    List<DebtSettlement> settlements = simplifyDebts(balances);
    auditLogs.add(
        String.format(
            "Settlement calculation complete: %d payment(s) to clear all debts.",
            settlements.size()));

    return new CalculationResult(
        totalExpenses,
        totalMonetary,
        totalServices,
        participants.size(),
        balances,
        settlements,
        auditLogs);
  }

  private Map<String, BigDecimal> computeShares(
      Expense expense,
      BigDecimal amount,
      List<String> allParticipants,
      Set<String> participantSet,
      Map<String, BigDecimal> incomeMap) {

    SplitType splitType = expense.splitType() != null ? expense.splitType() : SplitType.EQUAL;
    Map<String, BigDecimal> shares = new HashMap<>();

    switch (splitType) {
      case EQUAL -> {
        List<String> recipients = getRecipients(expense, allParticipants, participantSet);
        distributeEqually(recipients, amount, shares);
      }
      case INCOME_PROPORTIONAL -> {
        List<String> recipients = getRecipients(expense, allParticipants, participantSet);
        BigDecimal totalIncome =
            recipients.stream()
                .map(r -> incomeMap.getOrDefault(r, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
          BigDecimal allocated = BigDecimal.ZERO;
          for (String r : recipients) {
            BigDecimal income = incomeMap.getOrDefault(r, BigDecimal.ZERO);
            BigDecimal share = amount.multiply(income).divide(totalIncome, 2, RoundingMode.HALF_UP);
            shares.put(r, share);
            allocated = allocated.add(share);
          }

          BigDecimal diff = amount.subtract(allocated);
          if (diff.compareTo(BigDecimal.ZERO) != 0 && !recipients.isEmpty()) {
            String primary = recipients.get(0);
            shares.put(primary, shares.get(primary).add(diff));
          }
        } else {
          // If no income provided, fallback to equal distribution
          distributeEqually(recipients, amount, shares);
        }
      }
      case EXACT -> {
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseSplit split : expense.splits()) {
          String p = split.participant().trim();
          if (!participantSet.contains(p)) {
            throw new IllegalArgumentException(
                "Exact split recipient '" + p + "' is not a participant");
          }
          BigDecimal splitAmt =
              split.amount() != null
                  ? split.amount().setScale(2, RoundingMode.HALF_UP)
                  : BigDecimal.ZERO;
          shares.put(p, splitAmt);
          sum = sum.add(splitAmt);
        }
        if (sum.subtract(amount).abs().compareTo(new BigDecimal("0.05")) > 0) {
          throw new IllegalArgumentException(
              String.format(
                  "Exact splits sum (%s) does not match total expense amount (%s)", sum, amount));
        }
      }
      case PERCENTAGE -> {
        BigDecimal percentageSum = BigDecimal.ZERO;
        BigDecimal allocated = BigDecimal.ZERO;
        List<ExpenseSplit> validSplits = new ArrayList<>();

        for (ExpenseSplit split : expense.splits()) {
          String p = split.participant().trim();
          if (!participantSet.contains(p)) {
            throw new IllegalArgumentException(
                "Percentage split recipient '" + p + "' is not a participant");
          }
          if (split.percentage() != null) {
            percentageSum = percentageSum.add(split.percentage());
            validSplits.add(split);
          }
        }

        if (percentageSum.subtract(HUNDRED).abs().compareTo(new BigDecimal("0.1")) > 0) {
          throw new IllegalArgumentException(
              "Percentage splits sum must equal 100%, got " + percentageSum + "%");
        }

        for (int i = 0; i < validSplits.size(); i++) {
          ExpenseSplit split = validSplits.get(i);
          BigDecimal share =
              amount.multiply(split.percentage()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
          allocated = allocated.add(share);
          shares.put(split.participant().trim(), share);
        }

        BigDecimal diff = amount.subtract(allocated);
        if (diff.compareTo(BigDecimal.ZERO) != 0 && !validSplits.isEmpty()) {
          String first = validSplits.get(0).participant().trim();
          shares.put(first, shares.get(first).add(diff));
        }
      }
    }
    return shares;
  }

  private List<String> getRecipients(
      Expense expense, List<String> allParticipants, Set<String> participantSet) {
    if (expense.sharedWith() == null || expense.sharedWith().isEmpty()) {
      return allParticipants;
    }
    List<String> list =
        expense.sharedWith().stream()
            .map(String::trim)
            .filter(participantSet::contains)
            .distinct()
            .toList();
    return list.isEmpty() ? allParticipants : list;
  }

  private void distributeEqually(
      List<String> recipients, BigDecimal amount, Map<String, BigDecimal> shares) {
    int count = recipients.size();
    BigDecimal base = amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
    BigDecimal remainder = amount.subtract(base.multiply(BigDecimal.valueOf(count)));
    int centsToAdd = remainder.multiply(HUNDRED).intValueExact();

    for (int i = 0; i < count; i++) {
      BigDecimal share = base;
      if (i < centsToAdd) {
        share = share.add(new BigDecimal("0.01"));
      }
      shares.put(recipients.get(i), share);
    }
  }

  private List<DebtSettlement> simplifyDebts(List<ParticipantBalance> balances) {
    class Node {
      final String name;
      BigDecimal amount;

      Node(String name, BigDecimal amount) {
        this.name = name;
        this.amount = amount;
      }
    }

    List<Node> debtors = new ArrayList<>();
    List<Node> creditors = new ArrayList<>();

    for (ParticipantBalance pb : balances) {
      if (pb.netBalance().compareTo(EPSILON) > 0) {
        creditors.add(new Node(pb.participant(), pb.netBalance()));
      } else if (pb.netBalance().compareTo(EPSILON.negate()) < 0) {
        debtors.add(new Node(pb.participant(), pb.netBalance().abs()));
      }
    }

    List<DebtSettlement> settlements = new ArrayList<>();

    while (!debtors.isEmpty() && !creditors.isEmpty()) {
      debtors.sort(Comparator.comparing((Node n) -> n.amount).reversed());
      creditors.sort(Comparator.comparing((Node n) -> n.amount).reversed());

      Node debtor = debtors.get(0);
      Node creditor = creditors.get(0);

      BigDecimal settleAmount =
          debtor.amount.min(creditor.amount).setScale(2, RoundingMode.HALF_UP);
      if (settleAmount.compareTo(BigDecimal.ZERO) > 0) {
        settlements.add(new DebtSettlement(debtor.name, creditor.name, settleAmount));
      }

      debtor.amount = debtor.amount.subtract(settleAmount);
      creditor.amount = creditor.amount.subtract(settleAmount);

      if (debtor.amount.compareTo(EPSILON) < 0) {
        debtors.remove(0);
      }
      if (creditor.amount.compareTo(EPSILON) < 0) {
        creditors.remove(0);
      }
    }

    return settlements;
  }
}
