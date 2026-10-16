package com.sharing.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sharing.app.model.CalculationResult;
import com.sharing.app.model.Expense;
import com.sharing.app.model.ExpenseSplit;
import com.sharing.app.model.SplitType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

  private SettlementCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new SettlementCalculator();
  }

  @Test
  void calculates_equal_split_correctly() {
    List<String> participants = List.of("Alice", "Bob", "Charlie");
    Expense dinner =
        new Expense(
            "1",
            "Dinner",
            "Alice",
            new BigDecimal("90.00"),
            SplitType.EQUAL,
            List.of(),
            List.of("Alice", "Bob", "Charlie"));

    CalculationResult result = calculator.calculate(participants, List.of(dinner));

    assertThat(result.totalExpenses()).isEqualByComparingTo("90.00");
    assertThat(result.participantCount()).isEqualTo(3);

    // Each owes 30.00. Alice paid 90.00 (net +60.00), Bob paid 0 (net -30.00), Charlie paid 0 (net
    // -30.00)
    assertThat(result.settlements()).hasSize(2);
    assertThat(result.settlements())
        .anyMatch(
            s ->
                s.debtor().equals("Bob")
                    && s.creditor().equals("Alice")
                    && s.amount().compareTo(new BigDecimal("30.00")) == 0)
        .anyMatch(
            s ->
                s.debtor().equals("Charlie")
                    && s.creditor().equals("Alice")
                    && s.amount().compareTo(new BigDecimal("30.00")) == 0);
  }

  @Test
  void handles_odd_cent_remainders_accurately() {
    List<String> participants = List.of("Alice", "Bob", "Charlie");
    Expense expense =
        new Expense(
            "1", "Hotel", "Alice", new BigDecimal("100.00"), SplitType.EQUAL, List.of(), List.of());

    CalculationResult result = calculator.calculate(participants, List.of(expense));

    // Sum of shares must equal exactly 100.00
    BigDecimal sumShares =
        result.balances().stream()
            .map(b -> b.totalShare())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sumShares).isEqualByComparingTo("100.00");

    // Sum of net balances must be zero
    BigDecimal netSum =
        result.balances().stream()
            .map(b -> b.netBalance())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(netSum).isEqualByComparingTo("0.00");
  }

  @Test
  void simplifies_multi_party_debts_minimally() {
    // Alice pays 100 for Alice and Bob (Bob owes Alice 50)
    // Bob pays 50 for Bob and Charlie (Charlie owes Bob 25)
    // Direct settlement: Charlie owes Alice 25, Bob owes Alice 25. Total = 2 transactions instead
    // of circular!
    List<String> participants = List.of("Alice", "Bob", "Charlie");
    Expense exp1 =
        new Expense(
            "1",
            "Dinner",
            "Alice",
            new BigDecimal("100.00"),
            SplitType.EQUAL,
            List.of(),
            List.of("Alice", "Bob"));
    Expense exp2 =
        new Expense(
            "2",
            "Taxi",
            "Bob",
            new BigDecimal("50.00"),
            SplitType.EQUAL,
            List.of(),
            List.of("Bob", "Charlie"));

    CalculationResult result = calculator.calculate(participants, List.of(exp1, exp2));

    // Alice paid 100, share 50 -> net +50
    // Bob paid 50, share 50+25 = 75 -> net -25
    // Charlie paid 0, share 25 -> net -25
    assertThat(result.settlements()).hasSize(2);
    assertThat(result.settlements())
        .anyMatch(
            s ->
                s.debtor().equals("Bob")
                    && s.creditor().equals("Alice")
                    && s.amount().compareTo(new BigDecimal("25.00")) == 0)
        .anyMatch(
            s ->
                s.debtor().equals("Charlie")
                    && s.creditor().equals("Alice")
                    && s.amount().compareTo(new BigDecimal("25.00")) == 0);
  }

  @Test
  void handles_exact_and_percentage_splits() {
    List<String> participants = List.of("Alice", "Bob");
    Expense exactExp =
        new Expense(
            "1",
            "Groceries",
            "Alice",
            new BigDecimal("40.00"),
            SplitType.EXACT,
            List.of(
                ExpenseSplit.ofAmount("Alice", new BigDecimal("15.00")),
                ExpenseSplit.ofAmount("Bob", new BigDecimal("25.00"))),
            List.of());

    CalculationResult result = calculator.calculate(participants, List.of(exactExp));
    assertThat(result.settlements()).hasSize(1);
    assertThat(result.settlements().get(0).debtor()).isEqualTo("Bob");
    assertThat(result.settlements().get(0).creditor()).isEqualTo("Alice");
    assertThat(result.settlements().get(0).amount()).isEqualByComparingTo("25.00");
  }

  @Test
  void throws_on_invalid_payer() {
    List<String> participants = List.of("Alice", "Bob");
    Expense invalid =
        new Expense(
            "1", "Flight", "Dave", new BigDecimal("100.00"), SplitType.EQUAL, List.of(), List.of());

    assertThatThrownBy(() -> calculator.calculate(participants, List.of(invalid)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Dave");
  }

  @Test
  void handles_income_proportional_split() {
    List<com.sharing.app.model.Participant> participants =
        List.of(
            com.sharing.app.model.Participant.of("ilo", new BigDecimal("1666")),
            com.sharing.app.model.Participant.of("mahefa", new BigDecimal("1345")),
            com.sharing.app.model.Participant.of("tanjona", new BigDecimal("380")),
            com.sharing.app.model.Participant.of("sanda", new BigDecimal("200")));

    // Total income = 3591. Loyer = 1400.
    Expense loyer =
        new Expense(
            "1",
            "loyer",
            "ilo",
            new BigDecimal("1400.00"),
            com.sharing.app.model.ContributionType.MONETARY,
            SplitType.INCOME_PROPORTIONAL,
            List.of(),
            List.of("ilo", "mahefa", "tanjona", "sanda"));

    CalculationResult result = calculator.calculateWithParticipants(participants, List.of(loyer));

    assertThat(result.totalExpenses()).isEqualByComparingTo("1400.00");
    assertThat(result.totalMonetaryExpenses()).isEqualByComparingTo("1400.00");
    assertThat(result.totalServicesValue()).isEqualByComparingTo("0.00");

    // ilo paid 1400, share ~ 649.37 -> net positive
    // others owe ilo
    assertThat(result.settlements()).isNotEmpty();
    assertThat(result.settlements()).allMatch(s -> s.creditor().equals("ilo"));
  }

  @Test
  void compensates_cash_debt_with_services() {
    List<com.sharing.app.model.Participant> participants =
        List.of(
            com.sharing.app.model.Participant.of("Alice"),
            com.sharing.app.model.Participant.of("Bob"));

    // Alice paid 100 EUR cash for both (Bob owes 50)
    Expense cash =
        new Expense(
            "1",
            "Hotel",
            "Alice",
            new BigDecimal("100.00"),
            com.sharing.app.model.ContributionType.MONETARY,
            SplitType.EQUAL,
            List.of(),
            List.of());

    // Bob provided 100 EUR worth of Service (Cleaning/Repairs) for both (Alice owes Bob 50)
    Expense service =
        new Expense(
            "2",
            "Painting Service",
            "Bob",
            new BigDecimal("100.00"),
            com.sharing.app.model.ContributionType.SERVICE,
            SplitType.EQUAL,
            List.of(),
            List.of());

    CalculationResult result =
        calculator.calculateWithParticipants(participants, List.of(cash, service));

    // Both paid/provided 100 total, fair share 100 each -> perfectly balanced, 0 settlements
    // needed!
    assertThat(result.totalExpenses()).isEqualByComparingTo("200.00");
    assertThat(result.totalMonetaryExpenses()).isEqualByComparingTo("100.00");
    assertThat(result.totalServicesValue()).isEqualByComparingTo("100.00");
    assertThat(result.settlements()).isEmpty();
    assertThat(result.balances()).allMatch(b -> b.isSettled());
  }
}
