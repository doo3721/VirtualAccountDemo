package net.likelion.bebc25.virtualaccountdemo.portone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import net.likelion.bebc25.virtualaccountdemo.payment.IssueForm;
import net.likelion.bebc25.virtualaccountdemo.payment.Payment;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PortOneClientTests {
    private MockRestServiceServer server;
    private PortOneClient client;
    private PortOneProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PortOneProperties();
        properties.setApiSecret("test-secret");
        properties.setStoreId("store-test");
        properties.setChannelKey("channel-key-test");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.portone.io");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneClient(builder.build(), properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://demo.example.com/webhooks/portone"})
    void sendsInstantVirtualAccountRequestWithServerCredentials(String webhookUrl) {
        properties.setWebhookUrl(webhookUrl);
        var expected = server.expect(requestTo("https://api.portone.io/payments/va-test/instant"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "PortOne test-secret"))
                .andExpect(content().json("""
                        {"storeId":"store-test","channelKey":"channel-key-test",
                         "orderName":"가상계좌 입금 테스트","amount":{"total":1000},"currency":"KRW",
                         "customer":{"name":{"full":"테스트"},"email":"test@example.com","phoneNumber":"01012345678"},
                         "method":{"virtualAccount":{"bank":"SHINHAN","expiry":{"validHours":24},"option":{"type":"NORMAL"}}}}
                        """));
        if (webhookUrl.isBlank()) {
            expected.andExpect(jsonPath("$.noticeUrls").doesNotExist());
        } else {
            expected.andExpect(jsonPath("$.noticeUrls[0]").value(webhookUrl));
        }
        expected.andRespond(withSuccess("{\"payment\":{\"pgTxId\":\"test\"}}", MediaType.APPLICATION_JSON));
        IssueForm form = new IssueForm();
        form.setCustomerName("테스트");
        form.setEmail("test@example.com");
        form.setPhoneNumber("010-1234-5678");
        client.issue(Payment.requested("va-test", form));
        server.verify();
    }

    @Test
    void parsesSinglePaymentResponseIncludingAccountAndIgnoresExtraFields() {
        server.expect(requestTo("https://api.portone.io/payments/va-test?storeId=store-test"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "PortOne test-secret"))
                .andRespond(withSuccess("""
                        {"id":"va-test","storeId":"store-test","status":"VIRTUAL_ACCOUNT_ISSUED","currency":"KRW",
                         "amount":{"total":1000,"taxFree":0},"channel":{"key":"channel-key-test","pgProvider":"TOSSPAYMENTS","name":"토스"},
                         "method":{"type":"PaymentMethodVirtualAccount","bank":"SHINHAN","accountNumber":"X12345",
                         "remitteeName":"테스트","expiredAt":"2026-10-30T09:00:00+09:00","accountType":"NORMAL"},
                         "updatedAt":"2026-10-29T00:00:00Z","transactionId":"tx-test"}
                        """, MediaType.APPLICATION_JSON));
        PaymentSnapshot result = client.getPayment("va-test");
        assertThat(result.method().accountNumber()).isEqualTo("X12345");
        assertThat(result.amount().total()).isEqualTo(1000L);
        assertThat(result.updatedAt()).isNotNull();
        server.verify();
    }

    @Test
    void rawProviderErrorAndSecretAreNotExposed() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("sensitive-provider-response test-secret").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getPayment("va-test"))
                .isInstanceOf(PortOneException.class).hasMessageContaining("401")
                .hasMessageNotContaining("test-secret").hasMessageNotContaining("sensitive-provider-response");
        server.verify();
    }

    @Test
    void missingConfigurationDoesNotCallProvider() {
        properties.setApiSecret("");
        assertThatThrownBy(() -> client.getPayment("va-test")).isInstanceOf(PortOneException.class);
        server.verify();
    }

    @Test
    void invalidWebhookUrlIsRejectedBeforeProviderCall() {
        properties.setWebhookUrl("http://localhost:8080/webhooks/portone");
        assertThatThrownBy(properties::requireConfigured).isInstanceOf(PortOneException.class)
                .hasMessageContaining("HTTPS");
        server.verify();
    }
}
