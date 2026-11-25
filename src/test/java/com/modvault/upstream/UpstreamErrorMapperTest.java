package com.modvault.upstream;

import com.modvault.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamErrorMapperTest {

    private final UpstreamErrorMapper mapper = new UpstreamErrorMapper();

    @Test
    void upstream400MapsToInvalidRequest() {
        ApiException e = mapper.map(400, "{\"error\":{\"message\":\"bad model\"}}");
        assertEquals("invalid_request_error", e.errorType().wireValue());
        assertEquals(400, e.httpStatus());
        assertTrue(e.getMessage().contains("bad model"));
    }

    @Test
    void upstream401MapsToApiError500() {
        ApiException e = mapper.map(401, "{\"error\":{\"message\":\"invalid key secret\"}}");
        assertEquals("api_error", e.errorType().wireValue());
        assertEquals(500, e.httpStatus());
        assertTrue(!e.getMessage().contains("secret"));
    }

    @Test
    void upstream403MapsToApiError500() {
        ApiException e = mapper.map(403, "{}");
        assertEquals("api_error", e.errorType().wireValue());
        assertEquals(500, e.httpStatus());
    }

    @Test
    void upstream404MapsToNotFound() {
        ApiException e = mapper.map(404, "{}");
        assertEquals("not_found_error", e.errorType().wireValue());
        assertEquals(404, e.httpStatus());
    }

    @Test
    void upstream429MapsToRateLimit() {
        ApiException e = mapper.map(429, "{}");
        assertEquals("rate_limit_error", e.errorType().wireValue());
        assertEquals(429, e.httpStatus());
    }

    @Test
    void upstream5xxMapsToOverloaded529() {
        ApiException e = mapper.map(500, "{}");
        assertEquals("overloaded_error", e.errorType().wireValue());
        assertEquals(529, e.httpStatus());
    }
}
