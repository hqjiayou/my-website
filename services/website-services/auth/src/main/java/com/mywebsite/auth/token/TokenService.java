package com.mywebsite.auth.token;

import com.mywebsite.auth.principal.AuthenticatedUser;
import java.util.Optional;

public interface TokenService {

    String createToken(AuthenticatedUser user);

    Optional<AuthenticatedUser> parseToken(String token);
}
