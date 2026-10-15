package com.sharing.app.endpoint.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.sharing.app.conf.FacadeIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TestControllerIT extends FacadeIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void can_render_test_endpoint_with_diagnostics() throws Exception {
    mockMvc
        .perform(get("/test"))
        .andExpect(status().isOk())
        .andExpect(view().name("test-index"))
        .andExpect(model().attributeExists("activeProfiles"))
        .andExpect(model().attributeExists("contextPath"))
        .andExpect(model().attributeExists("uptime"))
        .andExpect(model().attributeExists("timestamp"))
        .andExpect(model().attributeExists("javaVersion"))
        .andExpect(content().string(containsString("Equitable Share App")))
        .andExpect(content().string(containsString("Operational")));
  }

  @Test
  void can_render_root_endpoint_with_diagnostics() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(view().name("test-index"))
        .andExpect(model().attributeExists("activeProfiles"));
  }
}
