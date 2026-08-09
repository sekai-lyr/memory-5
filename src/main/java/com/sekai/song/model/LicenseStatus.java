package com.sekai.song.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class LicenseStatus {
    private String plan;
    private LocalDateTime expireTime;
    private boolean valid;
    private boolean lifetime;
    private boolean expired;
    private boolean trial;
    private boolean pro;
    private boolean vip;
    private boolean canUseCloudImport;
    private boolean canUploadLocalMedia;
    private boolean canUseBilibiliVideo;
    private long remainingDays;
    private String label;
    private String message;
    private String actionText;

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isLifetime() {
        return lifetime;
    }

    public void setLifetime(boolean lifetime) {
        this.lifetime = lifetime;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public boolean isTrial() {
        return trial;
    }

    public void setTrial(boolean trial) {
        this.trial = trial;
    }

    public boolean isPro() {
        return pro;
    }

    public void setPro(boolean pro) {
        this.pro = pro;
    }

    public boolean isVip() {
        return vip;
    }

    public void setVip(boolean vip) {
        this.vip = vip;
    }

    public boolean isCanUseCloudImport() {
        return canUseCloudImport;
    }

    public void setCanUseCloudImport(boolean canUseCloudImport) {
        this.canUseCloudImport = canUseCloudImport;
    }

    public boolean isCanUploadLocalMedia() {
        return canUploadLocalMedia;
    }

    public void setCanUploadLocalMedia(boolean canUploadLocalMedia) {
        this.canUploadLocalMedia = canUploadLocalMedia;
    }

    public boolean isCanUseBilibiliVideo() {
        return canUseBilibiliVideo;
    }

    public void setCanUseBilibiliVideo(boolean canUseBilibiliVideo) {
        this.canUseBilibiliVideo = canUseBilibiliVideo;
    }

    public long getRemainingDays() {
        return remainingDays;
    }

    public void setRemainingDays(long remainingDays) {
        this.remainingDays = remainingDays;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getActionText() {
        return actionText;
    }

    public void setActionText(String actionText) {
        this.actionText = actionText;
    }

    public static LicenseStatus from(User user) {
        LicenseStatus status = new LicenseStatus();
        String plan = user == null || user.getLicensePlan() == null || user.getLicensePlan().isBlank()
                ? "TRIAL"
                : user.getLicensePlan().trim().toUpperCase();
        LocalDateTime expire = user == null ? null : user.getLicenseExpireTime();
        LocalDateTime now = LocalDateTime.now();
        boolean lifetime = "LIFETIME".equals(plan);
        boolean pro = "PRO".equals(plan);
        boolean trial = "TRIAL".equals(plan);
        boolean valid = lifetime || (expire != null && expire.isAfter(now));
        boolean vip = valid && (lifetime || pro);
        long remainingDays = lifetime
                ? Long.MAX_VALUE
                : expire == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(now, expire) + 1);

        status.setPlan(plan);
        status.setExpireTime(expire);
        status.setLifetime(lifetime);
        status.setPro(pro);
        status.setTrial(trial);
        status.setValid(valid);
        status.setExpired(!valid);
        status.setVip(vip);
        status.setCanUseCloudImport(vip);
        status.setCanUploadLocalMedia(vip);
        status.setCanUseBilibiliVideo(vip);
        status.setRemainingDays(remainingDays);
        status.setLabel(labelOf(plan));
        status.setMessage(messageOf(status));
        status.setActionText(actionOf(status));
        return status;
    }

    private static String labelOf(String plan) {
        return switch (plan) {
            case "PRO" -> "专业版 VIP";
            case "LIFETIME" -> "永久 VIP";
            case "TRIAL" -> "试用版";
            default -> plan;
        };
    }

    private static String messageOf(LicenseStatus status) {
        if (status.isLifetime()) {
            return "永久 VIP 已生效";
        }
        if (status.isValid()) {
            return status.getLabel() + "剩余 " + status.getRemainingDays() + " 天";
        }
        return "授权已过期，请激活新的授权码";
    }

    private static String actionOf(LicenseStatus status) {
        if (status.isVip()) {
            return "VIP 高级功能可用";
        }
        if (status.isValid()) {
            return "升级 VIP 解锁批量导入和本地媒体绑定";
        }
        return "激活授权码继续使用";
    }
}
