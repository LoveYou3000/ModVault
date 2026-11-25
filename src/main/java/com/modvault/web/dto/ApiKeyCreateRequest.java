package com.modvault.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiKeyCreateRequest {

    private String name;

    /** Optional ISO-8601 expiry timestamp. */
    @JsonProperty("expires_at")
    private String expiresAt;
}
