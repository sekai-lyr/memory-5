package com.sekai.song.service;

import com.sekai.song.model.BusinessLead;

import java.util.List;

public interface BusinessLeadService {
    boolean create(BusinessLead lead);

    List<BusinessLead> listRecent();

    long countOpen();

    boolean updateStatus(Long id, String status);
}
