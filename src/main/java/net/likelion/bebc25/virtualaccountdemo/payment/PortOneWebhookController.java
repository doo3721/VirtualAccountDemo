package net.likelion.bebc25.virtualaccountdemo.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import net.likelion.bebc25.virtualaccountdemo.portone.PortOneException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PortOneWebhookController {
    private final PaymentService service;

    public PortOneWebhookController(PaymentService service) { this.service = service; }

    @PostMapping(value = "/webhooks/portone", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestBody Webhook webhook) {
        log.info("PortOne 웹훅 수신");
        if (webhook.data() == null) {
            log.info("PortOne 웹훅 무시: data 없음");
            return;
        }
        try {
            service.handleWebhook(webhook.type(), webhook.data().storeId(), webhook.data().paymentId());
        } catch (PortOneException e) {
            log.warn("PortOne 웹훅 처리 실패 (재전송 필요): {}", e.getMessage());
            // 조회/검증 실패 시 2xx를 보내지 않아 PortOne의 재전송을 허용합니다.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PortOne 결제 재조회 또는 검증 실패");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Webhook(String type, Data data) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String storeId, String paymentId) {}
}
