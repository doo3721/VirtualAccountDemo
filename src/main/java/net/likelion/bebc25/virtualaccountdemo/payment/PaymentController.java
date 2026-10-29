package net.likelion.bebc25.virtualaccountdemo.payment;

import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import net.likelion.bebc25.virtualaccountdemo.portone.PortOneException;
import net.likelion.bebc25.virtualaccountdemo.portone.PortOneProperties;

@Controller
public class PaymentController {
    private static final String ISSUE_TOKEN = "issueToken";
    private final PaymentService service;
    private final PortOneProperties properties;

    public PaymentController(PaymentService service, PortOneProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/")
    public String home() { return "redirect:/payments"; }

    @GetMapping("/payments")
    public String list(Model model) {
        model.addAttribute("payments", service.recent());
        model.addAttribute("configured", properties.isConfigured());
        return "payments/list";
    }

    @GetMapping("/payments/new")
    public String form(Model model, HttpSession session) {
        IssueForm form = new IssueForm();
        String token = UUID.randomUUID().toString();
        session.setAttribute(ISSUE_TOKEN, token);
        form.setRequestToken(token);
        model.addAttribute("form", form);
        return showForm(model);
    }

    @PostMapping("/payments")
    public String issue(@Valid @ModelAttribute("form") IssueForm form, BindingResult errors,
                        Model model, HttpSession session, RedirectAttributes redirect) {
        if (errors.hasErrors()) { return showForm(model); }
        // 같은 브라우저의 더블 클릭/뒤로가기 재전송은 동일 결제 ID를 사용합니다.
        synchronized (session) {
            if (!form.getRequestToken().equals(session.getAttribute(ISSUE_TOKEN))) {
                errors.reject("token", "발급 화면이 만료되었습니다. 새 발급 화면에서 다시 시도해 주세요.");
                return showForm(model);
            }
            try {
                Payment payment = service.issue("va-" + form.getRequestToken(), form);
                return "redirect:/payments/" + payment.getId();
            } catch (PortOneException e) {
                errors.reject("portone", e.getMessage());
                return showForm(model);
            }
        }
    }

    @GetMapping("/payments/{id}")
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("payment", service.get(id));
        return "payments/detail";
    }

    @PostMapping("/payments/{id}/sync")
    public String sync(@PathVariable String id, RedirectAttributes redirect) {
        try {
            service.synchronize(id);
            redirect.addFlashAttribute("message", "PortOne에서 최신 결제 상태를 확인했습니다.");
        } catch (PortOneException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/payments/" + id;
    }

    private String showForm(Model model) {
        model.addAttribute("banks", Bank.values());
        model.addAttribute("configured", properties.isConfigured());
        return "payments/new";
    }
}
