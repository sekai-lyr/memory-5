package com.sekai.song.control;

import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.User;
import com.sekai.song.service.LicenseService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class LicenseModelAdvice {
    @Resource
    private LicenseService licenseService;

    @ModelAttribute("licenseStatus")
    public LicenseStatus licenseStatus(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return licenseService.statusOf(user);
        }
        return null;
    }
}
