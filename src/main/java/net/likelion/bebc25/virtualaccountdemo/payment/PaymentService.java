package net.likelion.bebc25.virtualaccountdemo.payment;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import net.likelion.bebc25.virtualaccountdemo.portone.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentService {
    private final PaymentRepository repository;
    private final PaymentStore store;
    private final PortOneClient client;
    private final PortOneProperties properties;

    public PaymentService(PaymentRepository repository, PaymentStore store,
                          PortOneClient client, PortOneProperties properties) {
        this.repository = repository;
        this.store = store;
        this.client = client;
        this.properties = properties;
    }

    public List<Payment> recent() { return repository.findTop50ByOrderByCreatedAtDesc(); }

    public Payment get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    public Payment issue(String id, IssueForm form) {
        properties.requireConfigured();
        if (repository.existsById(id)) { return get(id); }
        // 외부 API 전에 주문을 커밋합니다. 타임아웃/웹훅 선도착에도 같은 ID로 복구 가능합니다.
        Payment payment = repository.saveAndFlush(Payment.requested(id, form));
        try {
            client.issue(payment);
            return synchronize(id);
        } catch (PortOneException e) {
            store.markIssueUncertain(id, e.getMessage());
            return get(id);
        }
    }

    public Payment synchronize(String id) {
        Payment payment = get(id);
        PaymentSnapshot snapshot = client.getPayment(id);
        validate(payment, snapshot);
        return store.apply(id, snapshot);
    }

    public void handleWebhook(String type, String storeId, String paymentId) {
        if (type == null || !List.of("Transaction.Ready", "Transaction.Paid",
                "Transaction.VirtualAccountIssued", "Transaction.PartialCancelled",
                "Transaction.Cancelled", "Transaction.Failed", "Transaction.PayPending",
                "Transaction.CancelPending").contains(type)) {
            log.info("PortOne 웹훅 무시: 지원 대상이 아닌 이벤트");
            return;
        }
        // 본문은 알림으로만 취급합니다. 모르는 주문은 무시하고 자체 주문만 API로 검증합니다.
        if (!Objects.equals(properties.getStoreId(), storeId)) {
            log.warn("PortOne 웹훅 무시: 설정된 상점 ID와 불일치");
            return;
        }
        if (paymentId == null || !repository.existsById(paymentId)) {
            log.info("PortOne 웹훅 무시: 이 서버에 저장되지 않은 결제");
            return;
        }
        Payment payment = get(paymentId);
        PaymentSnapshot snapshot = client.getPayment(paymentId);
        validate(payment, snapshot);
        // 통지 직후 조회 상태 반영이 지연되면 502로 응답하여 재전송을 유도합니다.
        // 알림의 Paid 문자열만으로 DB를 완료 처리하지 않습니다.
        if ("Transaction.Paid".equals(type) && List.of("READY", "PENDING", "PAY_PENDING",
                "VIRTUAL_ACCOUNT_ISSUED").contains(snapshot.status())) {
            throw new PortOneException("입금 완료 통지의 조회 결과가 아직 입금 대기입니다. 웹훅 재전송이 필요합니다.");
        }
        Payment updated = store.applyWebhook(paymentId, snapshot, type);
        log.info("PortOne 웹훅 처리 완료: paymentId={}, event={}, status={}",
                updated.getId(), type, updated.getStatus());
    }

    private void validate(Payment payment, PaymentSnapshot snapshot) {
        if (!payment.getId().equals(snapshot.id())
                || !properties.getStoreId().equals(snapshot.storeId())
                || !"KRW".equals(snapshot.currency())
                || snapshot.amount() == null || snapshot.amount().total() == null
                || payment.getAmount() != snapshot.amount().total()
                || snapshot.channel() == null
                || !properties.getChannelKey().equals(snapshot.channel().key())
                || !"TOSSPAYMENTS".equals(snapshot.channel().pgProvider())
                || snapshot.updatedAt() == null) {
            throw new PortOneException("결제 ID·상점·채널·금액·통화 검증에 실패하여 상태를 반영하지 않았습니다.");
        }
        try {
            PaymentStatus.valueOf(snapshot.status());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new PortOneException("지원하지 않는 PortOne 결제 상태입니다. 상태를 반영하지 않았습니다.");
        }
        if (List.of("VIRTUAL_ACCOUNT_ISSUED", "PAID").contains(snapshot.status())
                && (snapshot.method() == null
                || !"PaymentMethodVirtualAccount".equals(snapshot.method().type())
                || snapshot.method().accountNumber() == null
                || !payment.getBank().name().equals(snapshot.method().bank()))) {
            throw new PortOneException("가상계좌 정보 검증에 실패하여 상태를 반영하지 않았습니다.");
        }
    }
}
