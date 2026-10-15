package com.sharing.app.service;

import com.sharing.app.model.CalculationResult;
import com.sharing.app.model.DebtSettlement;
import com.sharing.app.model.Expense;
import com.sharing.app.model.ExpenseSplit;
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
    List<String> participants =
        rawParticipants == null
            ? List.of()
            : rawParticipants.stream()
                .filter(p -> p != null && !p.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();

    List<String> auditLogs = new ArrayList<>();
    if (participants.isEmpty()) {
      return new CalculationResult(
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          0,
          Collections.emptyList(),
          Collections.emptyList(),
          List.of("No participants provided."));
    }

    Set<String> participantSet = new HashSet<>(participants);
    Map<String, BigDecimal> totalPaidMap = new HashMap<>();
    Map<String, BigDecimal> totalShareMap = new HashMap<>();
    participants.forEach(
        p -> {
          totalPaidMap.put(p, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
          totalShareMap.put(p, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        });

    BigDecimal totalExpenses = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    if (expenses != null) {
      for (Expense expense : expenses) {
        if (expense == null
            || expense.amount() == null
            || expense.amount().compareTo(BigDecimal.ZERO) <= 0) {
          continue;
        }

        String payer = expense.paidBy() != null ? expense.paidBy().trim() : "";
        if (!participantSet.contains(payer)) {
          throw new IllegalArgumentException(
              "Expense payer '" + payer + "' is not in the participants list");
        }

        BigDecimal amount = expense.amount().setScale(2, RoundingMode.HALF_UP);
        totalExpenses = totalExpenses.add(amount);
        totalPaidMap.put(payer, totalPaidMap.get(payer).add(amount));

        Map<String, BigDecimal> expenseShares =
            computeShares(expense, amount, participants, participantSet);
        expenseShares.forEach(
            (participant, share) ->
                totalShareMap.put(participant, totalShareMap.get(participant).add(share)));

        auditLogs.add(
            String.format(
                "Expense '%s' of %s %s paid by %s split as %s across %d participant(s)",
                expense.description(),
                amount,
                "EUR",
                payer,
                expense.splitType(),
                expenseShares.size()));
      }
    }

    List<ParticipantBalance> balances = new ArrayList<>();
    for (String p : participants) {
      BigDecimal paid = totalPaidMap.get(p);
      BigDecimal share = totalShareMap.get(p);
      BigDecimal net = paid.subtract(share).setScale(2, RoundingMode.HALF_UP);
      balances.add(new ParticipantBalance(p, paid, share, net));
    }

    List<DebtSettlement> settlements = simplifyDebts(balances);
    auditLogs.add(
        String.format(
            "Settlement calculation complete: %d payment(s) to clear all debts.",
            settlements.size()));

    return new CalculationResult(
        totalExpenses, participants.size(), balances, settlements, auditLogs);
  }

  private Map<String, BigDecimal> computeShares(
      Expense expense,
      BigDecimal amount,
      List<String> allParticipants,
      Set<String> participantSet) {

    SplitType splitType = expense.splitType() != null ? expense.splitType() : SplitType.EQUAL;
    Map<String, BigDecimal> shares = new HashMap<>();

    switch (splitType) {
      case EQUAL -> {
        List<String> recipients =
            expense.sharedWith() == null || expense.sharedWith().isEmpty()
                ? allParticipants
                : expense.sharedWith().stream()
                    .map(String::trim)
                    .filter(participantSet::contains)
                    .distinct()
                    .toList();

        if (recipients.isEmpty()) {
          recipients = allParticipants;
        }

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

        // Adjust rounding delta to first participant
        BigDecimal diff = amount.subtract(allocated);
        if (diff.compareTo(BigDecimal.ZERO) != 0 && !validSplits.isEmpty()) {
          String first = validSplits.get(0).participant().trim();
          shares.put(first, shares.get(first).add(diff));
        }
      }
    }
    return shares;
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
