package net.likelion.bebc25.virtualaccountdemo.portone;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import net.likelion.bebc25.virtualaccountdemo.payment.Payment;

@Component
public class PortOneClient {
    private final RestClient client;
    private final PortOneProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public PortOneClient(RestClient.Builder builder, PortOneProperties properties) {
        this.properties = properties;
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = builder.baseUrl("https://api.portone.io").requestFactory(factory).build();
    }

    PortOneClient(RestClient client, PortOneProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void issue(Payment payment) {
        properties.requireConfigured();
        // 발급 응답은 요약 정보뿐이므로 계좌 정보는 이어지는 단건 조회에서 가져옵니다.
        var body = new HashMap<String, Object>(Map.of(
                "storeId", properties.getStoreId(),
                "channelKey", properties.getChannelKey(),
                "orderName", payment.getOrderName(),
                "amount", Map.of("total", payment.getAmount()),
                "currency", "KRW",
                "customer", Map.of("name", Map.of("full", payment.getCustomerName()),
                        "email", payment.getEmail(), "phoneNumber", payment.getPhoneNumber()),
                "method", Map.of("virtualAccount", Map.of(
                        "bank", payment.getBank().name(),
                        "expiry", Map.of("validHours", payment.getValidHours()),
                        "option", Map.of("type", "NORMAL")))));
        if (StringUtils.hasText(properties.getWebhookUrl())) {
            body.put("noticeUrls", List.of(properties.getWebhookUrl()));
        }
        try {
            client.post().uri("/payments/{id}/instant", payment.getId())
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .body(body).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            // PG의 원본 응답이나 요청 헤더에 있는 개인정보/키를 화면과 로그에 노출하지 않습니다.
            throw new PortOneException("가상계좌 발급 요청을 확인하지 못했습니다 (HTTP "
                    + e.getStatusCode().value() + "). 채널 설정을 확인하고 같은 결제의 상태를 조회해 주세요.");
        } catch (RestClientException e) {
            throw new PortOneException("PortOne 발급 응답을 받지 못했습니다. 중복 발급을 피하려면 이 결제의 상태를 먼저 조회해 주세요.");
        }
    }

    public PaymentSnapshot getPayment(String paymentId) {
        properties.requireConfigured();
        try {
            PaymentSnapshot snapshot = client.get()
                    .uri(uri -> uri.path("/payments/{id}")
                            .queryParam("storeId", properties.getStoreId()).build(paymentId))
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .retrieve().body(PaymentSnapshot.class);
            if (snapshot == null) {
                throw new PortOneException("PortOne에서 빈 결제 응답을 받았습니다.");
            }
            return snapshot;
        } catch (RestClientResponseException e) {
            throw new PortOneException("결제 상태 조회에 실패했습니다 (HTTP " + e.getStatusCode().value()
                    + "). API Secret과 상점 설정을 확인하고 잠시 후 다시 조회해 주세요.");
        } catch (RestClientException e) {
            throw new PortOneException("PortOne 결제 상태를 가져오지 못했습니다. 잠시 후 다시 조회해 주세요.");
        }
    }

    private String authorization() {
        return "PortOne " + properties.getApiSecret();
    }
}
