package com.moviepick.backend.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.config.TossPaymentsProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 결제 승인(confirm) API 호출 담당. 결제창에서 돌려받은 paymentKey/orderId/amount가 실제로
 * 이 결제 건과 일치하는지 토스 서버가 직접 검증해줍니다(클라이언트가 결제 금액을 조작해서 보내는 것을
 * 막아줍니다) - 이 승인이 성공해야만 결제가 실제로 확정됩니다.
 */
@Component
public class TossPaymentsClient {

    private final RestClient tossRestClient;
    private final TossPaymentsProperties tossPaymentsProperties;
    private final ObjectMapper objectMapper;

    public TossPaymentsClient(RestClient tossRestClient, TossPaymentsProperties tossPaymentsProperties, ObjectMapper objectMapper) {
        this.tossRestClient = tossRestClient;
        this.tossPaymentsProperties = tossPaymentsProperties;
        this.objectMapper = objectMapper;
    }

    public void confirm(String paymentKey, String orderId, long amount) {
        String basicAuth = "Basic "
                + Base64.getEncoder().encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

        try {
            tossRestClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Authorization", basicAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new ApiException("결제 승인에 실패했습니다: " + extractMessage(e.getResponseBodyAsString()), HttpStatus.PAYMENT_REQUIRED);
        }
    }

    private String extractMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.has("message") ? node.get("message").asText() : body;
        } catch (Exception e) {
            return body;
        }
    }
}
