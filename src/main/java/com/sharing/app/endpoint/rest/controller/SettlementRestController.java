package com.sharing.app.endpoint.rest.controller;

import com.sharing.app.model.CalculationResult;
import com.sharing.app.service.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementRestController {

  private final SettlementCalculator settlementCalculator;

  @PostMapping("/calculate")
  public ResponseEntity<CalculationResult> calculate(@RequestBody CalculateRequest request) {
    CalculationResult result =
        settlementCalculator.calculate(request.participants(), request.expenses());
    return ResponseEntity.ok(result);
  }
}
