package com.sekai.song.service;

import com.sekai.song.model.BusinessOrder;
import com.sekai.song.model.BusinessOrderSummary;

import java.util.List;

public interface BusinessOrderService {
    boolean create(BusinessOrder order);

    List<BusinessOrder> listRecent();

    BusinessOrderSummary summary();

    boolean updateStatus(Long id, String status, String paymentStatus);
}
