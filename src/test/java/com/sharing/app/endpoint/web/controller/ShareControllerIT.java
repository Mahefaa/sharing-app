package com.sharing.app.endpoint.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.sharing.app.conf.FacadeIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ShareControllerIT extends FacadeIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void can_render_calculator_home_page() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(view().name("share-index"))
        .andExpect(model().attributeExists("shareForm"))
        .andExpect(model().attribute("hasResult", false))
        .andExpect(content().string(containsString("Equitable Expense Sharing")));
  }

  @Test
  void can_render_calculator_share_endpoint() throws Exception {
    mockMvc
        .perform(get("/share"))
        .andExpect(status().isOk())
        .andExpect(view().name("share-index"))
        .andExpect(model().attributeExists("shareForm"));
  }

  @Test
  void can_run_trip_demo_scenario() throws Exception {
    mockMvc
        .perform(get("/demo"))
        .andExpect(status().isOk())
        .andExpect(view().name("share-index"))
        .andExpect(model().attribute("hasResult", true))
        .andExpect(model().attributeExists("result"))
        .andExpect(content().string(containsString("Settlement Summary")))
        .andExpect(content().string(containsString("Minimal Settlement Plan")));
  }

  @Test
  void can_calculate_custom_form_submission() throws Exception {
    mockMvc
        .perform(
            post("/calculate")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("participantsInput", "Alice, Bob, Charlie")
                .param("expenses[0].description", "Hotel")
                .param("expenses[0].paidBy", "Alice")
                .param("expenses[0].amount", "90.00")
                .param("expenses[0].splitType", "EQUAL")
                .param("expenses[0].sharedWithInput", "Alice, Bob, Charlie"))
        .andExpect(status().isOk())
        .andExpect(view().name("share-index"))
        .andExpect(model().attribute("hasResult", true))
        .andExpect(model().attributeExists("result"))
        .andExpect(content().string(containsString("90.00 €")));
  }

  @Test
  void shows_error_message_when_less_than_two_participants() throws Exception {
    mockMvc
        .perform(
            post("/calculate")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("participantsInput", "Solo")
                .param("expenses[0].description", "Coffee")
                .param("expenses[0].paidBy", "Solo")
                .param("expenses[0].amount", "5.00"))
        .andExpect(status().isOk())
        .andExpect(view().name("share-index"))
        .andExpect(model().attribute("hasResult", false))
        .andExpect(model().attributeExists("errorMessage"))
        .andExpect(content().string(containsString("Please provide at least 2 participants")));
  }
}
