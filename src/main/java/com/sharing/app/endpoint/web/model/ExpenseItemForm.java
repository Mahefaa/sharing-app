package com.sharing.app.endpoint.web.model;

import com.sharing.app.model.SplitType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseItemForm {
  private String description;
  private String paidBy;
  private BigDecimal amount;
  private SplitType splitType = SplitType.EQUAL;
  private String sharedWithInput;
}
