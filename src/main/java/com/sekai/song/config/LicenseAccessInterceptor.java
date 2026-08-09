package com.sekai.song.config;

import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.User;
import com.sekai.song.service.LicenseService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LicenseAccessInterceptor implements HandlerInterceptor {
    @Resource
    private LicenseService licenseService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (isAllowedPath(path)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }
        Object loginUser = session.getAttribute("loginUser");
        if (!(loginUser instanceof User user)) {
            return true;
        }
        LicenseStatus status = licenseService.statusOf(user);
        if (status.isValid()) {
            return true;
        }
        response.sendRedirect("/license");
        return false;
    }

    private boolean isAllowedPath(String path) {
        return path == null
                || path.equals("/")
                || path.equals("/login")
                || path.equals("/register")
                || path.equals("/logout")
                || path.equals("/license")
                || path.equals("/license/activate")
                || path.equals("/service")
                || path.equals("/service/leads")
                || path.equals("/error")
                || path.startsWith("/user/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/uploads/")
                || path.startsWith("/external-uploads/");
    }
}
