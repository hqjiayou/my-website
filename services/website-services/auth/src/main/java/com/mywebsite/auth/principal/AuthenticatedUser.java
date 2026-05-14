package com.mywebsite.auth.principal;

import java.util.Set;

public record AuthenticatedUser(String subject, String username, Set<String> authorities) {
}
