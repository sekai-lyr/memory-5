package com.sekai.song.control;

import com.sekai.song.model.BusinessLead;
import com.sekai.song.model.User;
import com.sekai.song.service.BusinessLeadService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BusinessControl {
    @Resource
    private BusinessLeadService businessLeadService;

    @GetMapping("/service")
    public String service() {
        return "service";
    }

    @PostMapping("/service/leads")
    public String createLead(@RequestParam(value = "name", required = false) String name,
                             @RequestParam(value = "contact", required = false) String contact,
                             @RequestParam(value = "plan", required = false) String plan,
                             @RequestParam(value = "budget", required = false) String budget,
                             @RequestParam(value = "message", required = false) String message,
                             RedirectAttributes redirectAttributes) {
        BusinessLead lead = new BusinessLead();
        lead.setName(name);
        lead.setContact(contact);
        lead.setPlan(plan);
        lead.setBudget(budget);
        lead.setMessage(message);
        boolean success = businessLeadService.create(lead);
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "咨询已经收到，我会尽快联系你确认部署细节。" : "请填写姓名、联系方式和想要的服务。");
        return "redirect:/service#consult";
    }

    @GetMapping("/business/leads")
    public String leads(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isBusinessAdmin(user)) {
            return "redirect:/home";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("leads", businessLeadService.listRecent());
        model.addAttribute("openLeadCount", businessLeadService.countOpen());
        return "business-leads";
    }

    @PostMapping("/business/leads/{id}/status")
    public String updateLeadStatus(@PathVariable Long id,
                                   @RequestParam("status") String status,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isBusinessAdmin(user)) {
            return "redirect:/home";
        }
        boolean success = businessLeadService.updateStatus(id, status);
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "线索状态已更新" : "线索状态更新失败");
        return "redirect:/business/leads";
    }

    private boolean isBusinessAdmin(User user) {
        return user != null && "sekai".equalsIgnoreCase(user.getUserName());
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }
}
