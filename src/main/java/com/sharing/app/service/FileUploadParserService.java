package com.sharing.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharing.app.model.ContributionType;
import com.sharing.app.model.Expense;
import com.sharing.app.model.Participant;
import com.sharing.app.model.SplitType;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadParserService {

  private final ObjectMapper objectMapper;

  public ParsedShareData parse(InputStream inputStream, String filename) {
    if (inputStream == null) {
      throw new IllegalArgumentException("Upload file content cannot be null.");
    }

    try {
      String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
      if (content.isEmpty()) {
        throw new IllegalArgumentException("Uploaded file is empty.");
      }

      if (content.startsWith("{") || content.startsWith("[")) {
        return parseJson(content);
      } else {
        return parseCsvOrText(content);
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse uploaded file '{}': {}", filename, e.getMessage(), e);
      throw new IllegalArgumentException("Invalid file format: " + e.getMessage(), e);
    }
  }

  private ParsedShareData parseJson(String json) throws Exception {
    JsonNode root = objectMapper.readTree(json);
    Map<String, Participant> participantMap = new HashMap<>();
    List<Expense> expenses = new ArrayList<>();

    if (root.has("participants") && root.get("participants").isArray()) {
      for (JsonNode pNode : root.get("participants")) {
        if (pNode.isTextual()) {
          String name = pNode.asText().trim();
          participantMap.put(name, Participant.of(name));
        } else if (pNode.isObject()) {
          String name = pNode.path("name").asText().trim();
          BigDecimal income =
              pNode.has("income") && !pNode.get("income").isNull()
                  ? new BigDecimal(pNode.get("income").asText())
                  : null;
          participantMap.put(name, new Participant(name, income));
        }
      }
    }

    JsonNode expensesNode =
        root.has("expenses")
            ? root.get("expenses")
            : (root.has("entries") ? root.get("entries") : (root.isArray() ? root : null));

    if (expensesNode != null && expensesNode.isArray()) {
      for (JsonNode eNode : expensesNode) {
        String description = eNode.path("description").asText("Expense");
        String paidBy =
            eNode.has("paidBy")
                ? eNode.path("paidBy").asText().trim()
                : eNode.path("payer").asText().trim();
        BigDecimal amount = new BigDecimal(eNode.path("amount").asText("0"));

        String typeStr =
            eNode.has("type")
                ? eNode.path("type").asText()
                : (eNode.has("contributionType")
                    ? eNode.path("contributionType").asText()
                    : "MONETARY");
        ContributionType contributionType =
            "SERVICE".equalsIgnoreCase(typeStr)
                ? ContributionType.SERVICE
                : ContributionType.MONETARY;

        String splitStr = eNode.path("splitType").asText("EQUAL");
        SplitType splitType;
        try {
          splitType = SplitType.valueOf(splitStr.toUpperCase());
        } catch (Exception ex) {
          splitType = SplitType.EQUAL;
        }

        List<String> sharedWith = new ArrayList<>();
        if (eNode.has("sharedWith") && eNode.get("sharedWith").isArray()) {
          for (JsonNode sNode : eNode.get("sharedWith")) {
            sharedWith.add(sNode.asText().trim());
          }
        }

        if (!paidBy.isEmpty() && !participantMap.containsKey(paidBy)) {
          participantMap.put(paidBy, Participant.of(paidBy));
        }
        for (String sw : sharedWith) {
          if (!sw.isEmpty() && !participantMap.containsKey(sw)) {
            participantMap.put(sw, Participant.of(sw));
          }
        }

        expenses.add(
            new Expense(
                UUID.randomUUID().toString(),
                description,
                paidBy,
                amount,
                contributionType,
                splitType,
                List.of(),
                sharedWith));
      }
    }

    return new ParsedShareData(new ArrayList<>(participantMap.values()), expenses);
  }

  private ParsedShareData parseCsvOrText(String text) {
    Map<String, Participant> participantMap = new HashMap<>();
    List<Expense> expenses = new ArrayList<>();

    List<String> lines = Arrays.asList(text.split("\\r?\\n"));
    boolean headerChecked = false;
    boolean isStandardCsvTable = false;

    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
        continue;
      }

      String[] parts = line.split("[,;\\t]");
      for (int i = 0; i < parts.length; i++) {
        parts[i] = parts[i].trim();
      }

      // 1. Tagged participant: PARTICIPANT,name,optional_income
      if (parts[0].equalsIgnoreCase("PARTICIPANT") && parts.length >= 2) {
        String name = parts[1];
        BigDecimal income =
            parts.length >= 3 && !parts[2].isEmpty() ? new BigDecimal(parts[2]) : null;
        participantMap.put(name, new Participant(name, income));
        continue;
      }

      // 2. Tagged entry: ENTRY,type,description,payer,amount,split_type,shared_with
      if (parts[0].equalsIgnoreCase("ENTRY") && parts.length >= 5) {
        String typeStr = parts[1];
        ContributionType type =
            "SERVICE".equalsIgnoreCase(typeStr)
                ? ContributionType.SERVICE
                : ContributionType.MONETARY;
        String desc = parts[2];
        String payer = parts[3];
        BigDecimal amount = new BigDecimal(parts[4]);
        SplitType splitType = SplitType.EQUAL;
        if (parts.length >= 6 && !parts[5].isEmpty()) {
          try {
            splitType = SplitType.valueOf(parts[5].toUpperCase());
          } catch (Exception ignored) {
          }
        }
        List<String> sharedWith = new ArrayList<>();
        if (parts.length >= 7 && !parts[6].isEmpty()) {
          sharedWith =
              Arrays.stream(parts[6].split("[:|/]"))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .toList();
        }

        if (!payer.isEmpty() && !participantMap.containsKey(payer)) {
          participantMap.put(payer, Participant.of(payer));
        }
        for (String sw : sharedWith) {
          if (!sw.isEmpty() && !participantMap.containsKey(sw)) {
            participantMap.put(sw, Participant.of(sw));
          }
        }

        expenses.add(
            new Expense(
                UUID.randomUUID().toString(),
                desc,
                payer,
                amount,
                type,
                splitType,
                List.of(),
                sharedWith));
        continue;
      }

      // 3. Simple CSV table format
      if (!headerChecked) {
        headerChecked = true;
        if (parts[0].equalsIgnoreCase("type")
            || parts[0].equalsIgnoreCase("description")
            || parts[0].equalsIgnoreCase("payer")
            || parts[0].equalsIgnoreCase("item")) {
          isStandardCsvTable = true;
          continue;
        }
      }

      if (isStandardCsvTable || parts.length >= 4) {
        // format: [type], description, payer, amount, [splitType], [sharedWith]
        int idx = 0;
        ContributionType type = ContributionType.MONETARY;
        if (parts[0].equalsIgnoreCase("SERVICE") || parts[0].equalsIgnoreCase("EXPENSE")) {
          type =
              "SERVICE".equalsIgnoreCase(parts[0])
                  ? ContributionType.SERVICE
                  : ContributionType.MONETARY;
          idx++;
        }

        if (parts.length - idx >= 3) {
          String desc = parts[idx];
          String payer = parts[idx + 1];
          BigDecimal amount = new BigDecimal(parts[idx + 2]);
          SplitType splitType = SplitType.EQUAL;
          if (parts.length - idx >= 4 && !parts[idx + 3].isEmpty()) {
            try {
              splitType = SplitType.valueOf(parts[idx + 3].toUpperCase());
            } catch (Exception ignored) {
            }
          }
          List<String> sharedWith = new ArrayList<>();
          if (parts.length - idx >= 5 && !parts[idx + 4].isEmpty()) {
            sharedWith =
                Arrays.stream(parts[idx + 4].split("[:|/]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
          }

          if (!payer.isEmpty() && !participantMap.containsKey(payer)) {
            participantMap.put(payer, Participant.of(payer));
          }
          for (String sw : sharedWith) {
            if (!sw.isEmpty() && !participantMap.containsKey(sw)) {
              participantMap.put(sw, Participant.of(sw));
            }
          }

          expenses.add(
              new Expense(
                  UUID.randomUUID().toString(),
                  desc,
                  payer,
                  amount,
                  type,
                  splitType,
                  List.of(),
                  sharedWith));
        }
      }
    }

    return new ParsedShareData(new ArrayList<>(participantMap.values()), expenses);
  }
}
