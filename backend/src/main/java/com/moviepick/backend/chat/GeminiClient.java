package com.moviepick.backend.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.config.GeminiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Gemini(Google AI Studio 무료 API) 호출 담당.
 * <p>
 * REST 엔드포인트: POST {baseUrl}/models/{model}:generateContent?key=API_KEY
 * 응답 JSON 구조: candidates[0].content.parts[0].text
 * responseSchema를 넘기면 Gemini가 그 스키마에 맞는 JSON 문자열만 text로 돌려줍니다(구조화 출력).
 */
@Slf4j
@Component
public class GeminiClient {

    private static final int RESPONSE_PREVIEW_LENGTH = 300;

    private final RestClient geminiRestClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    public GeminiClient(RestClient geminiRestClient, GeminiProperties geminiProperties, ObjectMapper objectMapper) {
        this.geminiRestClient = geminiRestClient;
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
    }

    public record ChatTurn(String role, String text) {
    }

    /**
     * 대화 맥락(history + 이번 사용자 메시지)을 넘겨 모델 응답 텍스트를 받습니다.
     * responseSchema가 있으면 그 구조의 JSON 문자열을, 없으면 자유 형식의 일반 텍스트를 돌려줍니다.
     */
    public String generate(String systemInstruction, List<ChatTurn> turns, ObjectNode responseSchema) {
        requireApiKey();

        ObjectNode body = objectMapper.createObjectNode();

        ObjectNode system = objectMapper.createObjectNode();
        system.putArray("parts").addObject().put("text", systemInstruction);
        body.set("systemInstruction", system);

        ArrayNode contents = body.putArray("contents");
        for (ChatTurn turn : turns) {
            ObjectNode content = objectMapper.createObjectNode();
            content.put("role", turn.role());
            content.putArray("parts").addObject().put("text", turn.text());
            contents.add(content);
        }

        ObjectNode generationConfig = objectMapper.createObjectNode();
        if (responseSchema != null) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.set("responseSchema", responseSchema);
            // 구조화 추출은 매번 같은 결과가 나와야 안정적이라 온도를 0으로 고정합니다(기본값은 들쭉날쭉해서
            // 같은 질문에도 가끔 필드를 못 뽑아내는 경우가 있었습니다).
            generationConfig.put("temperature", 0);
        }
        body.set("generationConfig", generationConfig);

        String responseBody;
        try {
            responseBody = geminiRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:generateContent")
                            .queryParam("key", geminiProperties.apiKey())
                            .build(geminiProperties.model()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 무료 티어는 모델별로 하루/분당 요청 수가 낮게 제한돼 있어(예: 일부 모델은 하루 20회) 테스트
            // 중에도 쉽게 걸립니다. gemini.model을 더 넉넉한 무료 한도의 모델로 바꾸는 것도 방법입니다.
            log.warn("Gemini API 요청 한도를 초과했습니다.", e);
            throw new ApiException(dailyQuotaExceededMessage(e), HttpStatus.TOO_MANY_REQUESTS);
        } catch (RestClientException e) {
            log.error("Gemini API 호출 자체가 실패했습니다.", e);
            throw new ApiException("Gemini API 호출에 실패했습니다: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }

        return extractText(responseBody);
    }

    // 429 응답의 quotaId로 "하루 한도 소진"인지("...PerDay...") 아니면 순간적으로 몰려서 걸린 분당 한도인지
    // 구분해서, 정말 하루 한도를 다 썼을 때만 "내일 다시 이용해주세요" 안내를 보여줍니다.
    private String dailyQuotaExceededMessage(HttpClientErrorException.TooManyRequests e) {
        String body = e.getResponseBodyAsString();
        boolean isDailyQuota = body != null && body.contains("PerDay");
        if (isDailyQuota) {
            return "오늘의 AI 추천 채팅 무료 사용 한도를 모두 사용했어요. 한도는 매일 초기화되니(태평양 시간 자정 기준) 내일 다시 이용해주세요.";
        }
        return "지금 AI 추천 요청이 몰려서 잠시 제한에 걸렸어요. 잠시 후 다시 시도해주세요.";
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode()) {
                throw new IllegalStateException("candidates[0].content.parts[0].text 경로를 찾을 수 없습니다.");
            }
            return textNode.asText();
        } catch (Exception e) {
            log.error("Gemini 응답을 해석하지 못했습니다. body={}", responseBody, e);
            throw new ApiException(
                    "Gemini 응답을 해석할 수 없습니다. API 키가 올바른지 확인해주세요. 응답 일부: " + preview(responseBody),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private String preview(String body) {
        if (body == null) {
            return "(응답 없음)";
        }
        return body.length() > RESPONSE_PREVIEW_LENGTH ? body.substring(0, RESPONSE_PREVIEW_LENGTH) + "..." : body;
    }

    private void requireApiKey() {
        if (geminiProperties.apiKey() == null || geminiProperties.apiKey().isBlank()) {
            throw new ApiException(
                    "Gemini API 키가 설정되지 않았습니다. application-local.yml의 gemini.api-key를 채워주세요.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
