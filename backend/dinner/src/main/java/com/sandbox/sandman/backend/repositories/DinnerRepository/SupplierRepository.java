// com.sandbox.sandman.backend.repositories.SupplierRepository.java
package com.sandbox.sandman.backend.repositories.DinnerRepository;

import com.sandbox.sandman.backend.model.dto.DinnerDto.SupplierOrderDto;
import com.sandbox.sandman.backend.model.entity.DinnerEntity.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, Integer> {

    @Query(value = """
        SELECT 
            s.supplier_id AS id,
            s.supplier_name AS supplierName, 
            s.contact_person AS contactPerson, 
            s.phone AS phone,
            s.email AS email,
            o.order_id AS orderId,
            o.order_date AS orderDate, 
            o.delivery_date AS deliveryDate, 
            o.status AS status, 
            o.notes AS notes
        FROM dinner.suppliers s
        JOIN dinner.orders o ON s.supplier_id = o.supplier_id
        """, nativeQuery = true)
    Page<SupplierOrderDto> findSupplierOrder(Pageable pageable);
}