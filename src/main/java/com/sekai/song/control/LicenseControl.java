package com.sekai.song.control;

import com.sekai.song.model.LicenseCode;
import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.Result;
import com.sekai.song.model.User;
import com.sekai.song.model.VipFeature;
import com.sekai.song.model.VipPlan;
import com.sekai.song.service.LicenseService;
import com.sekai.song.service.VipQuotaService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class LicenseControl {
    @Resource
    private LicenseService licenseService;

    @Resource
    private VipQuotaService vipQuotaService;

    @GetMapping("/license")
    public String license(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        LicenseStatus status = licenseService.statusOf(user);
        model.addAttribute("loginUser", user);
        model.addAttribute("licenseStatus", status);
        model.addAttribute("vipFeatures", vipFeatures(status));
        model.addAttribute("vipPlans", vipPlans());
        model.addAttribute("vipUsage", vipQuotaService.usageOf(user));
        return "license";
    }

    @PostMapping("/license/activate")
    public String activate(@RequestParam("code") String code,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        Result<User> result = licenseService.activate(user.getId(), code);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("error", result.getMessage());
            return "redirect:/license";
        }
        session.setAttribute("loginUser", result.getData());
        redirectAttributes.addFlashAttribute("message", "授权激活成功");
        return "redirect:/license";
    }

    @GetMapping("/business/licenses")
    public String licenses(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isLicenseAdmin(user)) {
            return "redirect:/license";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("codes", licenseService.listCodes());
        model.addAttribute("vipPlans", vipPlans());
        return "business-licenses";
    }

    @PostMapping("/business/licenses/generate")
    public String generate(@RequestParam(value = "plan", defaultValue = "PRO") String plan,
                           @RequestParam(value = "durationDays", required = false) Integer durationDays,
                           @RequestParam(value = "count", defaultValue = "1") Integer count,
                           @RequestParam(value = "maxActivations", defaultValue = "1") Integer maxActivations,
                           @RequestParam(value = "note", required = false) String note,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isLicenseAdmin(user)) {
            redirectAttributes.addFlashAttribute("error", "当前账号没有生成授权码的权限");
            return "redirect:/license";
        }
        List<LicenseCode> generated = licenseService.generateCodes(plan, durationDays, count, maxActivations, note);
        redirectAttributes.addFlashAttribute("message", "已生成 " + generated.size() + " 个授权码");
        redirectAttributes.addFlashAttribute("generatedCodes", generated);
        return "redirect:/business/licenses";
    }

    private boolean isLicenseAdmin(User user) {
        return user != null && "sekai".equalsIgnoreCase(user.getUserName());
    }

    private List<VipFeature> vipFeatures(LicenseStatus status) {
        boolean vip = status != null && status.isVip();
        return List.of(
                new VipFeature("网易云 / QQ 批量导入", "支持按歌曲 ID、单曲链接或批量文本导入歌曲信息，适合快速搭建客户歌单。", "VIP", vip),
                new VipFeature("本地音频上传绑定", "把客户自己准备的 FLAC、MP3 等文件绑定到项目播放器，交付后可离线播放。", "VIP", vip),
                new VipFeature("B 站视频音乐本地绑定", "支持把下载好的本地视频绑定到 B 站视频音乐条目，避免在线播放卡顿。", "VIP", vip),
                new VipFeature("音乐资产洞察中心", "按商业交付标准分析本地化率、歌词覆盖率、来源结构、歌单健康度，并生成可直接放到商品页的卖点文案。", "VIP", vip),
                new VipFeature("歌单体检与高级维护", "扫描缺失本地文件、第三方链接风险、重复歌曲，并支持一键清理歌单重复项。", "VIP", vip),
                new VipFeature("客户交付体检报告", "按客户部署标准检查可播放率、本地化率、uploads 文件体积和缺文件风险，并导出交付报告。", "VIP", vip),
                new VipFeature("智能歌单工厂", "按来源、本地化状态、收藏、关键词和随机规则自动生成歌单，适合大歌库客户交付。", "VIP", vip),
                new VipFeature("专属主题与商业授权", "适合给客户部署后作为付费版交付，配合授权码续费和订单台账。", "PRO", vip),
                new VipFeature("基础歌单与播放器", "新账号可试用基础添加、歌单管理和播放器功能，方便先体验再购买。", "试用", status != null && status.isValid())
        );
    }

    private List<VipPlan> vipPlans() {
        return List.of(
                new VipPlan("VIP 月卡", "¥39", "PRO", 30, "适合短期试用、一次部署验收。", false),
                new VipPlan("VIP 季卡", "¥99", "PRO", 90, "适合客户稳定使用和小范围维护。", true),
                new VipPlan("VIP 年卡", "¥199", "PRO", 365, "推荐作为标准交付授权。", false),
                new VipPlan("永久 VIP", "¥399", "LIFETIME", null, "一次部署长期使用，适合高客单定制。", false)
        );
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }
}
