package com.modvault.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SetKeyRequest {

    /** The plaintext provider key (encrypted at rest, returned masked on read). */
    private String key;
}
