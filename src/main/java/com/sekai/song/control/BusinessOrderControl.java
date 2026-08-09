package com.sekai.song.control;

import com.sekai.song.model.BusinessOrder;
import com.sekai.song.model.User;
import com.sekai.song.service.BusinessOrderService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class BusinessOrderControl {
    @Resource
    private BusinessOrderService businessOrderService;

    @GetMapping("/business/orders")
    public String orders(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isBusinessAdmin(user)) {
            return "redirect:/home";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("summary", businessOrderService.summary());
        model.addAttribute("orders", businessOrderService.listRecent());
        return "business-orders";
    }

    @PostMapping("/business/orders/add")
    public String add(@RequestParam(value = "customerName", required = false) String customerName,
                      @RequestParam(value = "contact", required = false) String contact,
                      @RequestParam(value = "servicePlan", required = false) String servicePlan,
                      @RequestParam(value = "totalAmount", required = false) String totalAmount,
                      @RequestParam(value = "paidAmount", required = false) String paidAmount,
                      @RequestParam(value = "paymentStatus", defaultValue = "UNPAID") String paymentStatus,
                      @RequestParam(value = "status", defaultValue = "QUOTED") String status,
                      @RequestParam(value = "dueDate", required = false) String dueDate,
                      @RequestParam(value = "note", required = false) String note,
                      HttpSession session,
                      RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isBusinessAdmin(user)) {
            return "redirect:/home";
        }
        BusinessOrder order = new BusinessOrder();
        order.setCustomerName(customerName);
        order.setContact(contact);
        order.setServicePlan(servicePlan);
        order.setTotalAmount(parseMoney(totalAmount));
        order.setPaidAmount(parseMoney(paidAmount));
        order.setPaymentStatus(paymentStatus);
        order.setStatus(status);
        order.setDueDate(parseDate(dueDate));
        order.setNote(note);
        boolean success = businessOrderService.create(order);
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "订单已加入台账" : "请至少填写客户、联系方式和服务套餐");
        return "redirect:/business/orders";
    }

    @PostMapping("/business/orders/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam("status") String status,
                               @RequestParam("paymentStatus") String paymentStatus,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isBusinessAdmin(user)) {
            return "redirect:/home";
        }
        boolean success = businessOrderService.updateStatus(id, status, paymentStatus);
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "订单状态已更新" : "订单状态更新失败");
        return "redirect:/business/orders";
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
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
