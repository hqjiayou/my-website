package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.AuthJwkSetDocumentDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 JWKS（仅公钥）；无需认证。路径与 {@code AUTH_JWKS_URI} / Resource Server {@code jwk-set-uri} 对齐。
 */
@RestController
public class AuthJwksWellKnownController {

    private final AuthJwkSetDocumentDto jwkSetDocument;

    public AuthJwksWellKnownController(AuthJwkSetDocumentDto jwkSetDocument) {
        this.jwkSetDocument = jwkSetDocument;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String jwks() {
        return jwkSetDocument.json();
    }
}
