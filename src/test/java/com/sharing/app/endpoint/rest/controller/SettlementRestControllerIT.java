package com.sharing.app.endpoint.rest.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharing.app.conf.FacadeIT;
import com.sharing.app.model.Expense;
import com.sharing.app.model.SplitType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SettlementRestControllerIT extends FacadeIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void can_calculate_settlements_via_rest_api() throws Exception {
    List<String> participants = List.of("Alice", "Bob", "Charlie");
    Expense exp =
        new Expense(
            "e1",
            "Dinner",
            "Alice",
            new BigDecimal("60.00"),
            SplitType.EQUAL,
            List.of(),
            List.of("Alice", "Bob", "Charlie"));

    CalculateRequest request = new CalculateRequest(participants, List.of(exp));

    mockMvc
        .perform(
            post("/api/settlements/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalExpenses").value(60.00))
        .andExpect(jsonPath("$.participantCount").value(3))
        .andExpect(jsonPath("$.settlements.length()").value(2))
        .andExpect(jsonPath("$.balances.length()").value(3));
  }

  @Test
  void can_upload_file_synchronously_via_rest_api() throws Exception {
    String jsonContent =
        """
        {
          "participants": [
            { "name": "ilo", "income": 1666 },
            { "name": "mahefa", "income": 1345 }
          ],
          "expenses": [
            {
              "type": "EXPENSE",
              "description": "loyer",
              "paidBy": "ilo",
              "amount": 1000.00,
              "splitType": "EQUAL"
            }
          ]
        }
        """;

    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile(
            "file",
            "data.json",
            "application/json",
            jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                    "/api/settlements/upload")
                .file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalExpenses").value(1000.00))
        .andExpect(jsonPath("$.participantCount").value(2))
        .andExpect(jsonPath("$.settlements.length()").value(1))
        .andExpect(jsonPath("$.settlements[0].debtor").value("mahefa"))
        .andExpect(jsonPath("$.settlements[0].creditor").value("ilo"))
        .andExpect(jsonPath("$.settlements[0].amount").value(500.00));
  }
}
