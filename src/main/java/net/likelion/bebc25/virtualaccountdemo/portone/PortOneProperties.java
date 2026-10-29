package net.likelion.bebc25.virtualaccountdemo.portone;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {
    private String apiSecret = "";
    private String storeId = "";
    private String channelKey = "";
    private String webhookUrl = "";

    public boolean isConfigured() {
        return StringUtils.hasText(apiSecret) && StringUtils.hasText(storeId)
                && StringUtils.hasText(channelKey);
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new PortOneException("PortOne 연동 설정이 필요합니다. application-local.yaml을 확인해 주세요.");
        }
        if (StringUtils.hasText(webhookUrl)) {
            try {
                var uri = java.net.URI.create(webhookUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getFragment() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new PortOneException("portone.webhook-url에는 외부에서 접근 가능한 HTTPS 웹훅 주소를 입력해 주세요.");
            }
        }
    }
}
