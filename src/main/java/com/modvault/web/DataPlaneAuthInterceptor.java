package com.modvault.web;

import com.modvault.common.ApiException;
import com.modvault.common.AnthropicErrorType;
import com.modvault.config.ModVaultProperties;
import com.modvault.dal.entity.ApiKey;
import com.modvault.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Data-plane authentication: accepts {@code x-api-key} or {@code Authorization: Bearer}.
 * When both are present, {@code x-api-key} wins and a warning is logged (A-05). Also
 * enforces the request-body size cap (413).
 */
@Component
public class DataPlaneAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataPlaneAuthInterceptor.class);

    public static final String ATTR_API_KEY = "modvault.apiKey";

    private final ApiKeyService apiKeyService;
    private final ModVaultProperties props;

    public DataPlaneAuthInterceptor(ApiKeyService apiKeyService, ModVaultProperties props) {
        this.apiKeyService = apiKeyService;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long contentLength = request.getContentLengthLong();
        if (contentLength > props.getMaxRequestBodyBytes()) {
            throw new ApiException(AnthropicErrorType.REQUEST_TOO_LARGE, "request body exceeds size limit");
        }

        String xApiKey = request.getHeader("x-api-key");
        String authorization = request.getHeader("Authorization");
        String presented;
        if (xApiKey != null && !xApiKey.isEmpty()) {
            if (authorization != null && !authorization.isEmpty()) {
                log.warn("request_id={} both x-api-key and Authorization present; using x-api-key",
                        request.getAttribute(RequestIdFilter.ATTR));
            }
            presented = xApiKey;
        } else if (authorization != null && authorization.startsWith("Bearer ")) {
            presented = authorization.substring("Bearer ".length()).trim();
        } else {
            throw ApiException.unauthorized("missing credentials: provide x-api-key or Authorization: Bearer");
        }

        ApiKey apiKey = apiKeyService.authenticate(presented);
        if (apiKey == null) {
            throw ApiException.unauthorized("invalid or revoked api key");
        }
        request.setAttribute(ATTR_API_KEY, apiKey);
        return true;
    }
}
