package com.sharing.app.endpoint.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class TestController {

  private final Environment environment;

  @GetMapping("/test")
  public String testIndex(HttpServletRequest request, Model model) {
    String[] activeProfiles = environment.getActiveProfiles();
    String profiles = activeProfiles.length == 0 ? "default" : String.join(", ", activeProfiles);
    long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
    long uptimeSeconds = uptimeMillis / 1000;

    model.addAttribute("activeProfiles", profiles);
    model.addAttribute(
        "contextPath", request.getContextPath().isEmpty() ? "/" : request.getContextPath());
    model.addAttribute("uptime", uptimeSeconds + "s");
    model.addAttribute("timestamp", Instant.now().toString());
    model.addAttribute("javaVersion", System.getProperty("java.version"));
    return "test-index";
  }
}
