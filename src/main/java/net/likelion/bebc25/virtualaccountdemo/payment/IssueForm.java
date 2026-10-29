package net.likelion.bebc25.virtualaccountdemo.payment;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueForm {
    @NotBlank(message = "주문명을 입력해 주세요.")
    @Size(max = 100, message = "주문명은 100자 이내로 입력해 주세요.")
    private String orderName = "가상계좌 입금 테스트";

    @NotNull(message = "금액을 입력해 주세요.")
    @Min(value = 100, message = "금액은 100원 이상이어야 합니다.")
    @Max(value = 1000000, message = "데모 금액은 100만원 이하여야 합니다.")
    private Long amount = 1000L;

    @NotBlank(message = "구매자 이름을 입력해 주세요.")
    @Size(max = 50, message = "이름은 50자 이내로 입력해 주세요.")
    private String customerName;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식을 확인해 주세요.")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "연락처를 입력해 주세요.")
    @Pattern(regexp = "01[016789]-?[0-9]{3,4}-?[0-9]{4}", message = "휴대폰 번호 형식을 확인해 주세요.")
    private String phoneNumber;

    @NotNull(message = "은행을 선택해 주세요.")
    private Bank bank = Bank.SHINHAN;

    @NotNull(message = "입금 기한을 입력해 주세요.")
    @Min(value = 1, message = "기한은 1시간 이상이어야 합니다.")
    @Max(value = 72, message = "데모 기한은 72시간 이하여야 합니다.")
    private Integer validHours = 24;

    @NotBlank
    private String requestToken;
}
