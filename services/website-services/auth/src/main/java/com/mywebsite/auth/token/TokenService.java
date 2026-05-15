package com.mywebsite.auth.token;

import java.util.Optional;

public interface TokenService {

    String createToken(TokenClaims claims);

    Optional<TokenClaims> parseToken(String token, TokenAudience expectedAudience);
}
