package com.mywebsite.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** RSA 密钥路径与 JWK kid（4G：RS256 签发 / JWKS 发布）。不将私钥内容写入配置。 */
@ConfigurationProperties(prefix = "mywebsite.auth.jwt")
public class AuthJwtKeyProperties {

    /**
     * PEM 私钥路径，可为 {@code classpath:...} 或相对/绝对文件路径。
     * 环境变量：{@code AUTH_JWT_PRIVATE_KEY_PATH}
     */
    private String privateKeyPath = "";

    /**
     * PEM 公钥路径。环境变量：{@code AUTH_JWT_PUBLIC_KEY_PATH}
     */
    private String publicKeyPath = "";

    /** JWT Header {@code kid}，须与 JWKS 中 {@code kid} 一致。环境变量：{@code AUTH_JWT_KEY_ID} */
    private String keyId = "auth-local-rs256-key-1";

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
}
