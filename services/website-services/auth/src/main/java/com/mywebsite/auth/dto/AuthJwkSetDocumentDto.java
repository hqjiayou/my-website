package com.mywebsite.auth.dto;

/** 预渲染的 JWKS JSON（仅公钥），供 {@code GET /.well-known/jwks.json} 返回。 */
public final class AuthJwkSetDocumentDto {

    private final String json;

    public AuthJwkSetDocumentDto(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }
}
