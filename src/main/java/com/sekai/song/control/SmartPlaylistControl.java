package com.sekai.song.control;

import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.SmartPlaylistResult;
import com.sekai.song.model.User;
import com.sekai.song.service.LicenseService;
import com.sekai.song.service.SmartPlaylistService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SmartPlaylistControl {
    @Resource
    private LicenseService licenseService;

    @Resource
    private SmartPlaylistService smartPlaylistService;

    @GetMapping("/smart-playlists")
    public String page(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        LicenseStatus status = licenseService.statusOf(user);
        model.addAttribute("loginUser", user);
        model.addAttribute("licenseStatus", status);
        if (status.isVip()) {
            model.addAttribute("stats", smartPlaylistService.statsOf(user));
        }
        return "smart-playlists";
    }

    @PostMapping("/smart-playlists/generate")
    public String generate(@RequestParam(value = "playlistName", required = false) String playlistName,
                           @RequestParam(value = "sourceMode", defaultValue = "all") String sourceMode,
                           @RequestParam(value = "keyword", required = false) String keyword,
                           @RequestParam(value = "favoriteOnly", defaultValue = "false") boolean favoriteOnly,
                           @RequestParam(value = "deduplicate", defaultValue = "false") boolean deduplicate,
                           @RequestParam(value = "shuffle", defaultValue = "false") boolean shuffle,
                           @RequestParam(value = "sortMode", defaultValue = "recent") String sortMode,
                           @RequestParam(value = "limit", required = false) Integer limit,
                           @RequestParam(value = "coverUrl", required = false) String coverUrl,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        LicenseStatus status = licenseService.statusOf(user);
        if (!status.isVip()) {
            redirectAttributes.addFlashAttribute("error", "智能歌单工厂是 VIP 高级功能，请先激活授权。");
            return "redirect:/license";
        }
        SmartPlaylistResult result = smartPlaylistService.generate(
                user, playlistName, sourceMode, keyword, favoriteOnly, deduplicate, shuffle, sortMode, limit, coverUrl);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("error", result.getMessage());
            return "redirect:/smart-playlists";
        }
        redirectAttributes.addFlashAttribute("message", result.getMessage());
        return "redirect:/playlists/" + result.getPlaylistId();
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }
}
