package com.mywebsite.auth.service;

import com.mywebsite.auth.dto.IssuedAccessTokenDto;
import com.mywebsite.auth.dto.TokenClaimsDto;
import com.mywebsite.common.security.TokenAudience;
import java.util.Optional;

public interface TokenService {

    String createAccessToken(IssuedAccessTokenDto issuance);

    Optional<TokenClaimsDto> parseToken(String token, TokenAudience expectedAudience);
}
