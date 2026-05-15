package com.mywebsite.auth.principal;

import com.mywebsite.auth.token.TokenAudience;
import java.util.Set;

public record AuthenticatedUser(TokenAudience audience, String subject, String username, Set<String> authorities) {
}
