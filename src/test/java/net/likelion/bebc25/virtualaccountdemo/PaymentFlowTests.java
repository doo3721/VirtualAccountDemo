package net.likelion.bebc25.virtualaccountdemo;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import net.likelion.bebc25.virtualaccountdemo.payment.*;
import net.likelion.bebc25.virtualaccountdemo.portone.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowTests {
    @Autowired MockMvc mvc;
    @Autowired PaymentRepository repository;
    @Autowired PaymentService service;
    @MockitoBean PortOneClient client;

    @BeforeEach
    void clear() { repository.deleteAll(); }

    @Test
    void pagesRenderAndRejectInvalidAmountAndMissingCsrf() throws Exception {
        mvc.perform(get("/payments")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("가상계좌 입금 테스트")));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/payments/new").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        mvc.perform(post("/payments").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/payments").session(session).with(csrf())
                        .param("requestToken", (String) session.getAttribute("issueToken"))
                        .param("orderName", "테스트").param("amount", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "amount"));
        assertThat(repository.count()).isZero();
        verifyNoInteractions(client);
    }

    @Test
    void doubleSubmissionIssuesOnceAndRendersAccount() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/payments/new").session(session)).andExpect(status().isOk());
        String token = (String) session.getAttribute("issueToken");
        String id = "va-" + token;
        when(client.getPayment(id)).thenReturn(snapshot(id, "VIRTUAL_ACCOUNT_ISSUED", 1000, 0));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/payments").session(session).with(csrf())
                            .param("requestToken", token).param("orderName", "가상계좌 테스트")
                            .param("amount", "1000").param("customerName", "테스트")
                            .param("email", "test@example.com").param("phoneNumber", "01012345678")
                            .param("bank", "SHINHAN").param("validHours", "24"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/payments/" + id));
        }
        verify(client, times(1)).issue(any());
        assertThat(repository.count()).isEqualTo(1);
        mvc.perform(get("/payments/" + id)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("X123456789")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("입금 대기")));
        mvc.perform(get("/payments")).andExpect(status().isOk());
    }

    @Test
    void timeoutKeepsOrderAndCanRecoverUsingSameId() {
        String id = "va-" + UUID.randomUUID();
        doThrow(new PortOneException("응답 시간 초과")).when(client).issue(any());
        Payment issued = service.issue(id, form());
        assertThat(issued.getStatus()).isEqualTo(PaymentStatus.ISSUE_UNCERTAIN);
        when(client.getPayment(id)).thenReturn(snapshot(id, "VIRTUAL_ACCOUNT_ISSUED", 1000, 0));
        assertThat(service.synchronize(id).getStatus()).isEqualTo(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED);
        assertThat(service.get(id).getLastError()).isNull();
        verify(client, times(1)).issue(any());
    }

    @Test
    void webhookRequeriesProviderAndDuplicateEventsAreIdempotent() throws Exception {
        String id = saved();
        when(client.getPayment(id)).thenReturn(snapshot(id, "VIRTUAL_ACCOUNT_ISSUED", 1000, 0));
        // 본문이 Paid라고 주장하더라도 API가 입금 대기이면 완료 처리하지 않습니다.
        mvc.perform(post("/webhooks/portone").contentType("application/json").content(webhook(id)))
                .andExpect(status().isBadGateway());
        assertThat(service.get(id).getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(service.get(id).getLastWebhookAt()).isNull();
        when(client.getPayment(id)).thenReturn(snapshot(id, "PAID", 1000, 1));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/webhooks/portone").contentType("application/json").content(webhook(id)))
                    .andExpect(status().isNoContent());
        }
        assertThat(service.get(id).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(service.get(id).getPaidAt()).isNotNull();
        assertThat(service.get(id).getLastWebhookAt()).isNotNull();
        assertThat(service.get(id).getLastWebhookType()).isEqualTo("Transaction.Paid");
        mvc.perform(get("/payments/" + id)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("입금 완료")));
    }

    @Test
    void depositWebhookUpdatesIssuedAccountWithoutManualSync() throws Exception {
        String id = saved();
        when(client.getPayment(id)).thenReturn(snapshot(id, "VIRTUAL_ACCOUNT_ISSUED", 1000, 0));
        mvc.perform(post("/webhooks/portone").contentType("application/json")
                        .content(webhook(id).replace("Transaction.Paid", "Transaction.VirtualAccountIssued")))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED);

        when(client.getPayment(id)).thenReturn(snapshot(id, "PAID", 1000, 1));
        mvc.perform(post("/webhooks/portone").contentType("application/json").content(webhook(id)))
                .andExpect(status().isNoContent());
        Payment paid = repository.findById(id).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paid.getLastWebhookType()).isEqualTo("Transaction.Paid");
        assertThat(paid.getLastWebhookAt()).isNotNull();
        verify(client, times(2)).getPayment(id);
        mvc.perform(get("/payments/" + id)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Transaction.Paid")));
    }

    @Test
    void providerFailureCanRecoverOnWebhookRetry() throws Exception {
        String id = saved();
        when(client.getPayment(id)).thenThrow(new PortOneException("일시적인 조회 실패"))
                .thenReturn(snapshot(id, "PAID", 1000, 1));
        mvc.perform(post("/webhooks/portone").contentType("application/json").content(webhook(id)))
                .andExpect(status().isBadGateway());
        assertThat(service.get(id).getLastWebhookAt()).isNull();
        mvc.perform(post("/webhooks/portone").contentType("application/json").content(webhook(id)))
                .andExpect(status().isNoContent());
        assertThat(service.get(id).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(service.get(id).getLastWebhookAt()).isNotNull();
    }

    @Test
    void mismatchedAmountIsRejectedAndWebhookCanBeRetried() throws Exception {
        String id = saved();
        when(client.getPayment(id)).thenReturn(snapshot(id, "PAID", 999, 1));
        mvc.perform(post("/webhooks/portone").contentType("application/json").content(webhook(id)))
                .andExpect(status().isBadGateway());
        assertThat(service.get(id).getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        when(client.getPayment(id)).thenThrow(new PortOneException("조회 실패"));
        mvc.perform(post("/payments/" + id + "/sync").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertThat(service.get(id).getStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    void mismatchedStoreCurrencyOrChannelCannotMarkPaid() {
        String id = saved();
        PaymentSnapshot valid = snapshot(id, "PAID", 1000, 1);
        PaymentSnapshot[] invalid = {
                new PaymentSnapshot(id, "other-store", valid.status(), "KRW", valid.amount(), valid.method(), valid.channel(), valid.updatedAt(), valid.paidAt()),
                new PaymentSnapshot(id, valid.storeId(), valid.status(), "USD", valid.amount(), valid.method(), valid.channel(), valid.updatedAt(), valid.paidAt()),
                new PaymentSnapshot(id, valid.storeId(), valid.status(), "KRW", valid.amount(), valid.method(), new PaymentSnapshot.Channel("other", "TOSSPAYMENTS"), valid.updatedAt(), valid.paidAt())
        };
        for (PaymentSnapshot response : invalid) {
            when(client.getPayment(id)).thenReturn(response);
            assertThatThrownBy(() -> service.synchronize(id)).isInstanceOf(PortOneException.class);
            assertThat(service.get(id).getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        }
    }

    @Test
    void oldSnapshotCannotRevertPaidStatus() {
        String id = saved();
        when(client.getPayment(id)).thenReturn(snapshot(id, "PAID", 1000, 1));
        service.synchronize(id);
        when(client.getPayment(id)).thenReturn(snapshot(id, "VIRTUAL_ACCOUNT_ISSUED", 1000, 0));
        service.synchronize(id);
        assertThat(service.get(id).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void unrelatedWebhooksAreIgnoredAndMissingPaymentReturns404() throws Exception {
        mvc.perform(post("/webhooks/portone").contentType("application/json").content(webhook("unknown")))
                .andExpect(status().isNoContent());
        String id = saved();
        mvc.perform(post("/webhooks/portone").contentType("application/json")
                        .content(webhook(id).replace("store-test", "foreign-store")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/webhooks/portone").contentType("application/json")
                        .content(webhook(id).replace("Transaction.Paid", "Future.NewEvent")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/payments/unknown")).andExpect(status().isNotFound());
        verifyNoInteractions(client);
    }

    private String saved() {
        String id = "va-" + UUID.randomUUID();
        repository.saveAndFlush(Payment.requested(id, form()));
        return id;
    }

    private IssueForm form() {
        IssueForm form = new IssueForm();
        form.setCustomerName("테스트");
        form.setEmail("test@example.com");
        form.setPhoneNumber("01012345678");
        return form;
    }

    private PaymentSnapshot snapshot(String id, String status, long amount, int hour) {
        OffsetDateTime time = OffsetDateTime.parse("2026-10-29T00:00:00Z").plusHours(hour);
        return new PaymentSnapshot(id, "store-test", status, "KRW", new PaymentSnapshot.Amount(amount),
                new PaymentSnapshot.Method("PaymentMethodVirtualAccount", "SHINHAN", "X123456789", "테스트 상점", time.plusDays(1)),
                new PaymentSnapshot.Channel("channel-key-test", "TOSSPAYMENTS"), time,
                "PAID".equals(status) ? time : null);
    }

    private String webhook(String id) {
        return """
                {"type":"Transaction.Paid","data":{"storeId":"store-test","paymentId":"%s"},"unknownField":true}
                """.formatted(id);
    }
}
