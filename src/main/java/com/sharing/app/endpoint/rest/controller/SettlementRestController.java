package com.sharing.app.endpoint.rest.controller;

import com.sharing.app.model.CalculationResult;
import com.sharing.app.service.FileUploadParserService;
import com.sharing.app.service.ParsedShareData;
import com.sharing.app.service.SettlementCalculator;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementRestController {

  private final SettlementCalculator settlementCalculator;
  private final FileUploadParserService fileUploadParserService;

  @PostMapping("/calculate")
  public ResponseEntity<CalculationResult> calculate(@RequestBody CalculateRequest request) {
    CalculationResult result =
        settlementCalculator.calculate(request.participants(), request.expenses());
    return ResponseEntity.ok(result);
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CalculationResult> uploadFileSynchronously(
      @RequestParam("file") MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Uploaded file cannot be empty.");
    }
    ParsedShareData parsed =
        fileUploadParserService.parse(file.getInputStream(), file.getOriginalFilename());
    CalculationResult result =
        settlementCalculator.calculateWithParticipants(parsed.participants(), parsed.expenses());
    return ResponseEntity.ok(result);
  }
}
