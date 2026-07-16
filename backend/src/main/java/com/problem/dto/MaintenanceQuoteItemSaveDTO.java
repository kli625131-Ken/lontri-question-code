package com.problem.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaintenanceQuoteItemSaveDTO {
    private String areaName;
    private String itemName;
    private BigDecimal quantity;
    private String unitName;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String notes;
}
