package net.likelion.bebc25.virtualaccountdemo.portone;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSnapshot(String id, String storeId, String status, String currency,
                              Amount amount, Method method, Channel channel,
                              OffsetDateTime updatedAt, OffsetDateTime paidAt) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(Long total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Method(String type, String bank, String accountNumber, String remitteeName,
                         OffsetDateTime expiredAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(String key, String pgProvider) {}
}
