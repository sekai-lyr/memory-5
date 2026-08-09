package com.sekai.song.service.impl;

import com.sekai.song.model.BusinessOrder;
import com.sekai.song.model.BusinessOrderSummary;
import com.sekai.song.service.BusinessOrderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@Service
public class BusinessOrderServiceImpl implements BusinessOrderService {
    private static final Set<String> ALLOWED_STATUS = Set.of("QUOTED", "IN_PROGRESS", "DELIVERED", "CLOSED", "CANCELED");
    private static final Set<String> ALLOWED_PAYMENT_STATUS = Set.of("UNPAID", "DEPOSIT", "PAID", "REFUNDED");

    private final JdbcTemplate jdbcTemplate;

    public BusinessOrderServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean create(BusinessOrder order) {
        if (order == null
                || isBlank(order.getCustomerName())
                || isBlank(order.getContact())
                || isBlank(order.getServicePlan())) {
            return false;
        }
        BigDecimal totalAmount = positiveOrZero(order.getTotalAmount());
        BigDecimal paidAmount = positiveOrZero(order.getPaidAmount());
        String status = normalize(order.getStatus(), "QUOTED", ALLOWED_STATUS);
        String paymentStatus = normalize(order.getPaymentStatus(), "UNPAID", ALLOWED_PAYMENT_STATUS);
        String note = trimToEmpty(order.getNote());
        return jdbcTemplate.update("""
                INSERT INTO sekai_song_business_order(
                    customer_name, contact, service_plan, total_amount, paid_amount,
                    payment_status, status, due_date, note
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                trim(order.getCustomerName()),
                trim(order.getContact()),
                trim(order.getServicePlan()),
                totalAmount,
                paidAmount,
                paymentStatus,
                status,
                order.getDueDate(),
                note.length() > 1000 ? note.substring(0, 1000) : note) > 0;
    }

    @Override
    public List<BusinessOrder> listRecent() {
        return jdbcTemplate.query("""
                SELECT id, customer_name, contact, service_plan, total_amount, paid_amount,
                       payment_status, status, due_date, note, create_time, update_time
                FROM sekai_song_business_order
                ORDER BY create_time DESC, id DESC
                LIMIT 200
                """, new BusinessOrderRowMapper());
    }

    @Override
    public BusinessOrderSummary summary() {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(total_amount), 0) AS total_amount,
                       COALESCE(SUM(paid_amount), 0) AS paid_amount,
                       COUNT(CASE WHEN status IN ('QUOTED', 'IN_PROGRESS', 'DELIVERED') THEN 1 END) AS open_order_count
                FROM sekai_song_business_order
                """, (rs, rowNum) -> {
            BigDecimal total = nullToZero(rs.getBigDecimal("total_amount"));
            BigDecimal paid = nullToZero(rs.getBigDecimal("paid_amount"));
            BusinessOrderSummary summary = new BusinessOrderSummary();
            summary.setTotalAmount(total);
            summary.setPaidAmount(paid);
            summary.setUnpaidAmount(maxZero(total.subtract(paid)));
            summary.setOpenOrderCount(rs.getLong("open_order_count"));
            return summary;
        });
    }

    @Override
    public boolean updateStatus(Long id, String status, String paymentStatus) {
        if (id == null) {
            return false;
        }
        String normalizedStatus = normalize(status, null, ALLOWED_STATUS);
        String normalizedPaymentStatus = normalize(paymentStatus, null, ALLOWED_PAYMENT_STATUS);
        if (normalizedStatus == null || normalizedPaymentStatus == null) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE sekai_song_business_order
                SET status = ?, payment_status = ?, update_time = NOW()
                WHERE id = ?
                """, normalizedStatus, normalizedPaymentStatus, id) > 0;
    }

    private BigDecimal positiveOrZero(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal maxZero(BigDecimal value) {
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private String normalize(String value, String defaultValue, Set<String> allowed) {
        if (isBlank(value)) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase();
        return allowed.contains(normalized) ? normalized : defaultValue;
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

    private static class BusinessOrderRowMapper implements RowMapper<BusinessOrder> {
        @Override
        public BusinessOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            BusinessOrder order = new BusinessOrder();
            order.setId(rs.getLong("id"));
            order.setCustomerName(rs.getString("customer_name"));
            order.setContact(rs.getString("contact"));
            order.setServicePlan(rs.getString("service_plan"));
            order.setTotalAmount(nullToZero(rs.getBigDecimal("total_amount")));
            order.setPaidAmount(nullToZero(rs.getBigDecimal("paid_amount")));
            order.setPaymentStatus(rs.getString("payment_status"));
            order.setStatus(rs.getString("status"));
            if (rs.getDate("due_date") != null) {
                order.setDueDate(rs.getDate("due_date").toLocalDate());
            }
            order.setNote(rs.getString("note"));
            order.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            order.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
            return order;
        }
    }
}
