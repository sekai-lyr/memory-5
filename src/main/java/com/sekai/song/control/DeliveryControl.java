package com.sekai.song.control;

import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.User;
import com.sekai.song.service.DeliveryReadinessService;
import com.sekai.song.service.LicenseService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class DeliveryControl {
    @Resource
    private LicenseService licenseService;

    @Resource
    private DeliveryReadinessService deliveryReadinessService;

    @GetMapping("/delivery-tools")
    public String deliveryTools(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        LicenseStatus status = licenseService.statusOf(user);
        model.addAttribute("loginUser", user);
        model.addAttribute("licenseStatus", status);
        if (status.isVip()) {
            model.addAttribute("report", deliveryReadinessService.buildReport(user));
        }
        return "delivery-tools";
    }

    @GetMapping("/delivery-tools/report.txt")
    public ResponseEntity<byte[]> deliveryReport(HttpSession session,
                                                 RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/login").build();
        }
        LicenseStatus status = licenseService.statusOf(user);
        if (!status.isVip()) {
            redirectAttributes.addFlashAttribute("error", "客户交付报告是 VIP 功能，请先激活授权。");
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/license").build();
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(LocalDateTime.now());
        byte[] bytes = deliveryReadinessService.renderTextReport(user).getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("sekai-song-delivery-" + timestamp + ".txt", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(bytes);
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }
}
