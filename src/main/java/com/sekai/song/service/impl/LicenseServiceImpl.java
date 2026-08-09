package com.sekai.song.service.impl;

import com.sekai.song.mapper.SekaiSongUserMapper;
import com.sekai.song.model.LicenseCode;
import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.Result;
import com.sekai.song.model.User;
import com.sekai.song.service.LicenseService;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LicenseServiceImpl implements LicenseService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<String> ALLOWED_PLANS = Set.of("TRIAL", "PRO", "LIFETIME");

    private final JdbcTemplate jdbcTemplate;

    @Resource
    private SekaiSongUserMapper userMapper;

    public LicenseServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LicenseStatus statusOf(User user) {
        return LicenseStatus.from(user);
    }

    @Override
    @Transactional
    public Result<User> activate(Long userId, String code) {
        if (userId == null || code == null || code.isBlank()) {
            return Result.fail("请输入授权码");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        String normalizedCode = normalizeCode(code);
        List<LicenseCode> codes = jdbcTemplate.query("""
                SELECT id, code, plan, duration_days, max_activations, used_count, enabled, note, create_time, update_time
                FROM sekai_song_license_code
                WHERE code = ?
                LIMIT 1
                """, new LicenseCodeRowMapper(), normalizedCode);
        if (codes.isEmpty()) {
            return Result.fail("授权码不存在");
        }
        LicenseCode licenseCode = codes.get(0);
        if (!Boolean.TRUE.equals(licenseCode.getEnabled())) {
            return Result.fail("授权码已停用");
        }
        int used = licenseCode.getUsedCount() == null ? 0 : licenseCode.getUsedCount();
        int max = licenseCode.getMaxActivations() == null ? 1 : licenseCode.getMaxActivations();
        if (used >= max) {
            return Result.fail("授权码已经被使用完");
        }

        String plan = normalizePlan(licenseCode.getPlan());
        Integer durationDays = licenseCode.getDurationDays();
        LocalDateTime expireTime = null;
        if (!"LIFETIME".equals(plan)) {
            int days = durationDays == null || durationDays <= 0 ? 30 : durationDays;
            LocalDateTime base = user.getLicenseExpireTime() != null && user.getLicenseExpireTime().isAfter(LocalDateTime.now())
                    ? user.getLicenseExpireTime()
                    : LocalDateTime.now();
            expireTime = base.plusDays(days);
        }

        userMapper.updateLicense(userId, plan, expireTime, normalizedCode);
        jdbcTemplate.update("""
                UPDATE sekai_song_license_code
                SET used_count = used_count + 1, update_time = NOW()
                WHERE id = ?
                """, licenseCode.getId());

        User updatedUser = userMapper.findById(userId);
        if (updatedUser != null) {
            updatedUser.setPassword(null);
        }
        return Result.success(updatedUser);
    }

    @Override
    public List<LicenseCode> generateCodes(String plan, Integer durationDays, Integer count, Integer maxActivations, String note) {
        String normalizedPlan = normalizePlan(plan);
        int safeCount = Math.min(Math.max(count == null ? 1 : count, 1), 50);
        int safeMaxActivations = Math.min(Math.max(maxActivations == null ? 1 : maxActivations, 1), 100);
        Integer safeDuration = "LIFETIME".equals(normalizedPlan) ? null : Math.max(durationDays == null ? 30 : durationDays, 1);
        String safeNote = note == null ? "" : note.trim();
        if (safeNote.length() > 200) {
            safeNote = safeNote.substring(0, 200);
        }

        List<LicenseCode> generated = new ArrayList<>();
        for (int i = 0; i < safeCount; i++) {
            String code = uniqueCode();
            jdbcTemplate.update("""
                    INSERT INTO sekai_song_license_code(code, plan, duration_days, max_activations, note)
                    VALUES (?, ?, ?, ?, ?)
                    """, code, normalizedPlan, safeDuration, safeMaxActivations, safeNote);
            generated.add(findCode(code));
        }
        return generated;
    }

    @Override
    public List<LicenseCode> listCodes() {
        return jdbcTemplate.query("""
                SELECT id, code, plan, duration_days, max_activations, used_count, enabled, note, create_time, update_time
                FROM sekai_song_license_code
                ORDER BY create_time DESC, id DESC
                LIMIT 200
                """, new LicenseCodeRowMapper());
    }

    private LicenseCode findCode(String code) {
        List<LicenseCode> results = jdbcTemplate.query("""
                SELECT id, code, plan, duration_days, max_activations, used_count, enabled, note, create_time, update_time
                FROM sekai_song_license_code
                WHERE code = ?
                LIMIT 1
                """, new LicenseCodeRowMapper(), code);
        if (results.isEmpty()) {
            throw new IllegalStateException("License code insert succeeded but select returned empty for: " + code);
        }
        return results.get(0);
    }

    private String uniqueCode() {
        while (true) {
            String code = "SK-" + block(4) + "-" + block(4) + "-" + block(4);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sekai_song_license_code WHERE code = ?",
                    Integer.class,
                    code);
            if (count == null || count == 0) {
                return code;
            }
        }
    }

    private String block(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private String normalizePlan(String plan) {
        String normalized = plan == null ? "PRO" : plan.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_PLANS.contains(normalized) ? normalized : "PRO";
    }

    private static class LicenseCodeRowMapper implements RowMapper<LicenseCode> {
        @Override
        public LicenseCode mapRow(ResultSet rs, int rowNum) throws SQLException {
            LicenseCode code = new LicenseCode();
            code.setId(rs.getLong("id"));
            code.setCode(rs.getString("code"));
            code.setPlan(rs.getString("plan"));
            int durationDays = rs.getInt("duration_days");
            code.setDurationDays(rs.wasNull() ? null : durationDays);
            code.setMaxActivations(rs.getInt("max_activations"));
            code.setUsedCount(rs.getInt("used_count"));
            code.setEnabled(rs.getBoolean("enabled"));
            code.setNote(rs.getString("note"));
            code.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            code.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
            return code;
        }
    }
}
