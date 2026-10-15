package com.sharing.app.endpoint.web.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareForm {
  private String participantsInput = "Alice, Bob, Charlie, David";
  private List<ExpenseItemForm> expenses = new ArrayList<>();
}
