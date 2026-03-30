// com.sandbox.sandman.backend.model.dto.SupplierOrderDTO.java
package com.sandbox.sandman.backend.model.dto.DinnerDto;

import java.time.LocalDate;

public interface SupplierOrderDto {
    Integer getId();
    String getSupplierName();
    String getContactPerson();
    String getPhone();
    String getEmail();
    Integer getOrderId();
    LocalDate getOrderDate();
    LocalDate getDeliveryDate();
    String getStatus();
    String getNotes();
}