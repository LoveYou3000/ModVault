package com.modvault.web;

import com.modvault.common.ApiException;
import com.modvault.config.ModVaultProperties;
import com.modvault.dal.entity.ApiKey;
import com.modvault.service.AdminTokenValidator;
import com.modvault.service.ApiKeyService;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInterceptorsTest {

    private static final String ADMIN_TOKEN = "0123456789012345678901234567890123456789";

    private HttpServletRequest requestWith(String xApiKey, String authorization) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("x-api-key")).thenReturn(xApiKey);
        when(req.getHeader("Authorization")).thenReturn(authorization);
        when(req.getContentLengthLong()).thenReturn(0L);
        return req;
    }

    @Test
    void dataPlanePrefersXApiKeyWhenBothPresent() {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ModVaultProperties props = new ModVaultProperties();
        DataPlaneAuthInterceptor interceptor = new DataPlaneAuthInterceptor(apiKeyService, props);
        HttpServletRequest req = requestWith("sk-mv-aaa", "Bearer sk-mv-bbb");
        when(apiKeyService.authenticate("sk-mv-aaa")).thenReturn(new ApiKey());

        assertTrue(interceptor.preHandle(req, mock(HttpServletResponse.class), null));
        verify(apiKeyService).authenticate("sk-mv-aaa");
    }

    @Test
    void dataPlaneUsesBearerWhenNoXApiKey() {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ModVaultProperties props = new ModVaultProperties();
        DataPlaneAuthInterceptor interceptor = new DataPlaneAuthInterceptor(apiKeyService, props);
        HttpServletRequest req = requestWith(null, "Bearer sk-mv-bbb");
        when(apiKeyService.authenticate("sk-mv-bbb")).thenReturn(new ApiKey());

        assertTrue(interceptor.preHandle(req, mock(HttpServletResponse.class), null));
        verify(apiKeyService).authenticate("sk-mv-bbb");
    }

    @Test
    void dataPlaneRejectsMissingCredentials() {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        DataPlaneAuthInterceptor interceptor = new DataPlaneAuthInterceptor(apiKeyService, new ModVaultProperties());
        HttpServletRequest req = requestWith(null, null);
        assertThrows(ApiException.class, () -> interceptor.preHandle(req, mock(HttpServletResponse.class), null));
    }

    @Test
    void adminAcceptsValidTokenAndRejectsInvalid() {
        ModVaultProperties props = new ModVaultProperties();
        props.setAdminToken(ADMIN_TOKEN);
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(new AdminTokenValidator(props));

        HttpServletRequest ok = requestWith(null, "Bearer " + ADMIN_TOKEN);
        assertTrue(interceptor.preHandle(ok, mock(HttpServletResponse.class), null));

        HttpServletRequest bad = requestWith(null, "Bearer wrong-token");
        assertThrows(ApiException.class, () -> interceptor.preHandle(bad, mock(HttpServletResponse.class), null));
    }
}
