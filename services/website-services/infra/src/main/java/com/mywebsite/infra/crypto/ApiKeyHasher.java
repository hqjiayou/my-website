package com.mywebsite.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 明文 API Key 的 SHA-256 小写十六进制摘要。admin 网关访问 Key 签发侧与 agent-app
 * 网关 {@code /v1/**} 鉴权过滤器共用同一实现，防止两侧哈希算法悄悄分叉导致有效 Key 被判无效。
 */
public final class ApiKeyHasher {

    private ApiKeyHasher() {}

    public static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
