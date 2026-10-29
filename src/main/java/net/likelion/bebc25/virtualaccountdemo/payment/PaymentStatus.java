package net.likelion.bebc25.virtualaccountdemo.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    REQUESTED("발급 요청 중"), ISSUE_UNCERTAIN("발급 확인 필요"), READY("결제 준비"),
    PENDING("처리 대기"), PAY_PENDING("승인 대기"),
    VIRTUAL_ACCOUNT_ISSUED("입금 대기"), PAID("입금 완료"), FAILED("결제 실패"),
    CANCELLED("취소 완료"), PARTIAL_CANCELLED("부분 취소");

    private final String label;
}
