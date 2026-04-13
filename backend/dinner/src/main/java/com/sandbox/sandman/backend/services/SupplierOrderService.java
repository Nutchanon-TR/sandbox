package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.common.PageResponse;
import com.sandbox.sandman.backend.model.common.PaginationRequest;
import com.sandbox.sandman.backend.model.dto.DinnerDto.SupplierOrderDto;
import com.sandbox.sandman.backend.repositories.DinnerRepository.SupplierRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SupplierOrderService {
@Autowired
    private SupplierRepository supplierRepository;

    public PageResponse<SupplierOrderDto> getAllSupplierOrders(PaginationRequest req) {
        log.info("Process inquiry");
        Page<SupplierOrderDto> page = supplierRepository.findSupplierOrder(req.toPageable());
        return PageResponse.toPageResponse(page);
    }
}
