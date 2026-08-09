package com.sekai.song.control;

import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.User;
import com.sekai.song.service.LicenseService;
import com.sekai.song.service.MusicInsightService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MusicInsightControl {
    @Resource
    private LicenseService licenseService;

    @Resource
    private MusicInsightService musicInsightService;

    @GetMapping("/insights")
    public String page(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        LicenseStatus status = licenseService.statusOf(user);
        model.addAttribute("loginUser", user);
        model.addAttribute("licenseStatus", status);
        if (status.isVip()) {
            model.addAttribute("report", musicInsightService.buildReport(user));
        }
        return "insights";
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }
}
