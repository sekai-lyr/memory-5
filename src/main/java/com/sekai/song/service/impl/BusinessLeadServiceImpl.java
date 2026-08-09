package com.sekai.song.service.impl;

import com.sekai.song.model.BusinessLead;
import com.sekai.song.service.BusinessLeadService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@Service
public class BusinessLeadServiceImpl implements BusinessLeadService {
    private static final Set<String> ALLOWED_STATUS = Set.of("NEW", "CONTACTED", "WON", "LOST");

    private final JdbcTemplate jdbcTemplate;

    public BusinessLeadServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean create(BusinessLead lead) {
        if (lead == null || isBlank(lead.getName()) || isBlank(lead.getContact()) || isBlank(lead.getPlan())) {
            return false;
        }
        String message = trimToEmpty(lead.getMessage());
        return jdbcTemplate.update("""
                INSERT INTO sekai_song_business_lead(name, contact, plan, budget, message, status)
                VALUES (?, ?, ?, ?, ?, 'NEW')
                """,
                trim(lead.getName()),
                trim(lead.getContact()),
                trim(lead.getPlan()),
                trimToEmpty(lead.getBudget()),
                message.length() > 1000 ? message.substring(0, 1000) : message) > 0;
    }

    @Override
    public List<BusinessLead> listRecent() {
        return jdbcTemplate.query("""
                SELECT id, name, contact, plan, budget, message, status, create_time, update_time
                FROM sekai_song_business_lead
                ORDER BY create_time DESC, id DESC
                LIMIT 100
                """, new BusinessLeadRowMapper());
    }

    @Override
    public long countOpen() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sekai_song_business_lead
                WHERE status IN ('NEW', 'CONTACTED')
                """, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        if (id == null || isBlank(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(normalized)) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE sekai_song_business_lead
                SET status = ?, update_time = NOW()
                WHERE id = ?
                """, normalized, id) > 0;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class BusinessLeadRowMapper implements RowMapper<BusinessLead> {
        @Override
        public BusinessLead mapRow(ResultSet rs, int rowNum) throws SQLException {
            BusinessLead lead = new BusinessLead();
            lead.setId(rs.getLong("id"));
            lead.setName(rs.getString("name"));
            lead.setContact(rs.getString("contact"));
            lead.setPlan(rs.getString("plan"));
            lead.setBudget(rs.getString("budget"));
            lead.setMessage(rs.getString("message"));
            lead.setStatus(rs.getString("status"));
            lead.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            lead.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
            return lead;
        }
    }
}
