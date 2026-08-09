package com.sekai.song.control;

import com.sekai.song.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class BusinessModelAdvice {
    @ModelAttribute("businessAdmin")
    public boolean businessAdmin(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user.getUserName() != null && "sekai".equalsIgnoreCase(user.getUserName());
        }
        return false;
    }
}
