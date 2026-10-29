package net.likelion.bebc25.virtualaccountdemo.payment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import net.likelion.bebc25.virtualaccountdemo.portone.PaymentSnapshot;

@Service
public class PaymentStore {
    private final PaymentRepository repository;

    public PaymentStore(PaymentRepository repository) { this.repository = repository; }

    @Transactional
    public Payment apply(String id, PaymentSnapshot snapshot) {
        Payment payment = locked(id);
        payment.apply(snapshot);
        return payment;
    }

    @Transactional
    public Payment applyWebhook(String id, PaymentSnapshot snapshot, String type) {
        Payment payment = locked(id);
        payment.apply(snapshot);
        payment.recordWebhook(type);
        return payment;
    }

    @Transactional
    public void markIssueUncertain(String id, String message) {
        locked(id).markIssueUncertain(message);
    }

    private Payment locked(String id) {
        return repository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
