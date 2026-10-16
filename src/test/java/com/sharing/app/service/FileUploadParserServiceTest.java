package com.sharing.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharing.app.model.ContributionType;
import com.sharing.app.model.SplitType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileUploadParserServiceTest {

  private FileUploadParserService parserService;

  @BeforeEach
  void setUp() {
    parserService = new FileUploadParserService(new ObjectMapper());
  }

  @Test
  void can_parse_json_file_with_services_and_incomes() {
    String json =
        """
        {
          "participants": [
            { "name": "ilo", "income": 1666 },
            { "name": "mahefa", "income": 1345 },
            { "name": "tanjona", "income": 380 },
            { "name": "sanda", "income": 200 }
          ],
          "expenses": [
            {
              "type": "EXPENSE",
              "description": "loyer",
              "paidBy": "ilo",
              "amount": 1400.00,
              "splitType": "INCOME_PROPORTIONAL",
              "sharedWith": ["ilo", "mahefa", "tanjona", "sanda"]
            },
            {
              "type": "SERVICE",
              "description": "cuisine sakafo",
              "paidBy": "sanda",
              "amount": 150.00,
              "splitType": "EQUAL",
              "sharedWith": ["ilo", "mahefa", "tanjona", "sanda"]
            }
          ]
        }
        """;

    ParsedShareData parsed =
        parserService.parse(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "colocation.json");

    assertThat(parsed.participants()).hasSize(4);
    assertThat(parsed.participants())
        .anyMatch(
            p ->
                p.name().equals("ilo") && p.declaredIncome().compareTo(new BigDecimal("1666")) == 0)
        .anyMatch(
            p ->
                p.name().equals("sanda")
                    && p.declaredIncome().compareTo(new BigDecimal("200")) == 0);

    assertThat(parsed.expenses()).hasSize(2);
    assertThat(parsed.expenses().get(0).description()).isEqualTo("loyer");
    assertThat(parsed.expenses().get(0).splitType()).isEqualTo(SplitType.INCOME_PROPORTIONAL);
    assertThat(parsed.expenses().get(1).contributionType()).isEqualTo(ContributionType.SERVICE);
  }

  @Test
  void can_parse_csv_file_with_tagged_entries() {
    String csv =
        """
        PARTICIPANT,ilo,1666
        PARTICIPANT,mahefa,1345
        PARTICIPANT,tanjona,380
        PARTICIPANT,sanda,200
        ENTRY,EXPENSE,loyer,ilo,1400,INCOME_PROPORTIONAL,ilo:mahefa:tanjona:sanda
        ENTRY,EXPENSE,sakafo,mahefa,200,INCOME_PROPORTIONAL,ilo:mahefa:tanjona:sanda
        ENTRY,SERVICE,Reparations,tanjona,100,EQUAL,ilo:mahefa:tanjona:sanda
        """;

    ParsedShareData parsed =
        parserService.parse(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "data.csv");

    assertThat(parsed.participants()).hasSize(4);
    assertThat(parsed.expenses()).hasSize(3);
    assertThat(parsed.expenses().get(2).contributionType()).isEqualTo(ContributionType.SERVICE);
    assertThat(parsed.expenses().get(2).paidBy()).isEqualTo("tanjona");
  }

  @Test
  void throws_on_empty_content() {
    assertThatThrownBy(
            () ->
                parserService.parse(
                    new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), "empty.csv"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
