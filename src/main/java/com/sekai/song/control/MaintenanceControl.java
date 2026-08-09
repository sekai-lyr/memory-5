package com.sekai.song.control;

import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.User;
import com.sekai.song.service.LicenseService;
import com.sekai.song.service.MaintenanceService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MaintenanceControl {
    @Resource
    private LicenseService licenseService;

    @Resource
    private MaintenanceService maintenanceService;

    @GetMapping("/vip-tools")
    public String tools(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        LicenseStatus status = licenseService.statusOf(user);
        model.addAttribute("loginUser", user);
        model.addAttribute("licenseStatus", status);
        if (status.isVip()) {
            model.addAttribute("report", maintenanceService.buildReport(user));
        }
        return "vip-tools";
    }

    @PostMapping("/vip-tools/playlists/{playlistId}/deduplicate")
    public String deduplicatePlaylist(@PathVariable Long playlistId,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        int removed = maintenanceService.deduplicatePlaylist(playlistId, user);
        redirectAttributes.addFlashAttribute(removed > 0 ? "message" : "error",
                removed > 0 ? "已从歌单里移除 " + removed + " 条重复歌曲引用" : "这个歌单暂时没有可清理的重复项");
        return "redirect:/vip-tools";
    }

    @PostMapping("/vip-tools/deduplicate-all")
    public String deduplicateAll(HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        int removed = maintenanceService.deduplicateAll(user);
        redirectAttributes.addFlashAttribute(removed > 0 ? "message" : "error",
                removed > 0 ? "已从所有歌单里移除 " + removed + " 条重复歌曲引用" : "当前没有可清理的重复项");
        return "redirect:/vip-tools";
    }

    private boolean requireVip(User user, RedirectAttributes redirectAttributes) {
        LicenseStatus status = licenseService.statusOf(user);
        if (status.isVip()) {
            return true;
        }
        redirectAttributes.addFlashAttribute("error", "歌单体检和高级维护是 VIP 功能，请先激活授权。");
        return false;
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }
}
