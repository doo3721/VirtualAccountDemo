package net.likelion.bebc25.virtualaccountdemo.payment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.virtualaccountdemo.portone.PaymentSnapshot;

@Entity
@Table(name = "demo_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @Column(length = 64)
    private String id;
    @Column(nullable = false, length = 100)
    private String orderName;
    @Column(nullable = false)
    private long amount;
    private String customerName;
    private String email;
    private String phoneNumber;
    @Enumerated(EnumType.STRING)
    private Bank bank;
    private int validHours;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    private String accountNumber;
    private String remitteeName;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    private OffsetDateTime syncedAt;
    private OffsetDateTime remoteUpdatedAt;
    private OffsetDateTime lastWebhookAt;
    private String lastWebhookType;
    @Column(length = 500)
    private String lastError;
    @Version
    private long version;

    public static Payment requested(String id, IssueForm form) {
        Payment payment = new Payment();
        payment.id = id;
        payment.orderName = form.getOrderName().trim();
        payment.amount = form.getAmount();
        payment.customerName = form.getCustomerName().trim();
        payment.email = form.getEmail().trim();
        payment.phoneNumber = form.getPhoneNumber().replace("-", "");
        payment.bank = form.getBank();
        payment.validHours = form.getValidHours();
        payment.status = PaymentStatus.REQUESTED;
        payment.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return payment;
    }

    public void markIssueUncertain(String message) {
        // 웹훅이 먼저 반영된 결제 상태를 발급 오류로 덮어쓰지 않습니다.
        if (status == PaymentStatus.REQUESTED) {
            status = PaymentStatus.ISSUE_UNCERTAIN;
            lastError = message;
        }
    }

    public void apply(PaymentSnapshot snapshot) {
        if (remoteUpdatedAt != null && snapshot.updatedAt().isBefore(remoteUpdatedAt)) {
            return;
        }
        status = PaymentStatus.valueOf(snapshot.status());
        if (snapshot.method() != null && snapshot.method().accountNumber() != null) {
            accountNumber = snapshot.method().accountNumber();
            remitteeName = snapshot.method().remitteeName();
            expiresAt = snapshot.method().expiredAt();
        }
        paidAt = snapshot.paidAt();
        remoteUpdatedAt = snapshot.updatedAt();
        syncedAt = OffsetDateTime.now(ZoneOffset.UTC);
        lastError = null;
    }

    public String getCreatedAtText() { return displayTime(createdAt); }
    public String getLastWebhookAtText() { return displayTime(lastWebhookAt); }
    public String getExpiresAtText() { return displayTime(expiresAt); }
    public String getPaidAtText() { return displayTime(paidAt); }
    public String getSyncedAtText() { return displayTime(syncedAt); }

    public void recordWebhook(String type) {
        lastWebhookAt = OffsetDateTime.now(ZoneOffset.UTC);
        lastWebhookType = type;
    }

    private String displayTime(OffsetDateTime time) {
        return time == null ? "—" : time.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
