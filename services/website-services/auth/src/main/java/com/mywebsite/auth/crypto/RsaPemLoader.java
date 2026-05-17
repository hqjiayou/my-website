package com.mywebsite.auth.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/** 从 PEM 文件加载 RSA 公私钥；支持 {@code classpath:} 前缀或文件系统路径。 */
public final class RsaPemLoader {

    private RsaPemLoader() {}

    public static RSAPrivateKey loadPrivateKeyPem(String path) {
        String pem = readPemString(path);
        byte[] der = parseSinglePemDer(pem, "PRIVATE KEY", "RSA PRIVATE KEY");
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("RSA private key invalid (PKCS#8 expected): " + summarizePath(path), e);
        }
    }

    public static RSAPublicKey loadPublicKeyPem(String path) {
        String pem = readPemString(path);
        byte[] der = parseSinglePemDer(pem, "PUBLIC KEY", "RSA PUBLIC KEY");
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("RSA public key invalid: " + summarizePath(path), e);
        }
    }

    private static String summarizePath(String path) {
        if (path.length() > 80) {
            return path.substring(0, 40) + "…";
        }
        return path;
    }

    static String readPemString(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("RSA key path is blank (set AUTH_JWT_PRIVATE_KEY_PATH / AUTH_JWT_PUBLIC_KEY_PATH or auth.jwt.*)");
        }
        try {
            if (path.startsWith("classpath:")) {
                String cp = path.substring("classpath:".length());
                Resource r = new ClassPathResource(cp);
                if (!r.exists()) {
                    throw new IllegalStateException("Classpath PEM not found: " + path);
                }
                try (InputStream in = r.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            Path p = Path.of(path);
            if (!p.isAbsolute()) {
                Path cwd = Path.of(System.getProperty("user.dir"));
                p = cwd.resolve(p).normalize();
                if (!Files.isRegularFile(p) && cwd.getParent() != null) {
                    Path sibling = cwd.getParent().resolve(Path.of(path)).normalize();
                    if (Files.isRegularFile(sibling)) {
                        p = sibling;
                    }
                }
            }
            if (!Files.isRegularFile(p)) {
                throw new IllegalStateException("RSA key file not found: " + p);
            }
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read RSA key PEM: " + path, e);
        }
    }

    private static byte[] parseSinglePemDer(String pem, String... labelAlternatives) {
        String normalized = pem.replace("\r\n", "\n").trim();
        for (String label : labelAlternatives) {
            String begin = "-----BEGIN " + label + "-----";
            String end = "-----END " + label + "-----";
            int b = normalized.indexOf(begin);
            int e = normalized.indexOf(end);
            if (b >= 0 && e > b) {
                String b64 =
                        normalized.substring(b + begin.length(), e).replace("\n", "").replace(" ", "");
                return Base64.getDecoder().decode(b64);
            }
        }
        throw new IllegalStateException(
                "PEM does not contain a recognized RSA block (expected " + String.join(" / ", labelAlternatives) + ")");
    }
}
