package com.mywebsite.auth.token;

import java.util.Optional;

public interface TokenService {

    String createAccessToken(IssuedAccessToken issuance);

    Optional<TokenClaims> parseToken(String token, TokenAudience expectedAudience);
}
