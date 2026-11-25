package com.modvault.upstream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modvault.common.ApiException;
import com.modvault.common.AnthropicErrorType;
import org.springframework.stereotype.Component;

/**
 * Maps upstream HTTP status + body to Anthropic error semantics (PRD appendix A.4).
 * Upstream 401/403 are platform-side faults and become 500 {@code api_error}, never a
 * client-facing 401.
 */
@Component
public class UpstreamErrorMapper {

    private static final int MAX_SUMMARY_LENGTH = 300;

    private final ObjectMapper mapper = new ObjectMapper();

    public ApiException map(UpstreamHttpException e) {
        return map(e.statusCode(), e.body());
    }

    public ApiException map(int statusCode, String body) {
        switch (statusCode) {
            case 400:
            case 422:
                return ApiException.badRequest("upstream rejected request: " + summarize(body));
            case 401:
            case 403:
                return ApiException.apiError("upstream authentication failed; check the configured provider key");
            case 404:
                return ApiException.notFound("upstream resource not found");
            case 429:
                return new ApiException(AnthropicErrorType.RATE_LIMIT_ERROR, "upstream rate limited");
            default:
                if (statusCode >= 500) {
                    return ApiException.overloaded("upstream service overloaded or unavailable");
                }
                return ApiException.apiError("upstream returned unexpected status " + statusCode);
        }
    }

    /**
     * Extracts a short, safe summary from an OpenAI-style error body. Never includes
     * the full body or any credential-like content.
     */
    private String summarize(String body) {
        if (body == null || body.isEmpty()) {
            return "no detail";
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode error = root.path("error");
            JsonNode message = error.isObject() ? error.path("message") : error;
            String text = message.isMissingNode() ? root.toString() : message.asText("");
            if (text.length() > MAX_SUMMARY_LENGTH) {
                text = text.substring(0, MAX_SUMMARY_LENGTH) + "...";
            }
            return text.isEmpty() ? "no detail" : text;
        } catch (JsonProcessingException ex) {
            return "no detail";
        }
    }
}
