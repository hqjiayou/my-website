package com.mywebsite.auth.tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * 一次性生成 {@code src/test/resources/jwt} 下测试用 RSA PEM（CI 与本地需相同固定密钥时可提交该目录）。
 * 运行：<code>mvn -q -f auth/pom.xml exec:java -Dexec.mainClass=com.mywebsite.auth.tool.GenerateTestPemMain -Dexec.classpathScope=test</code>
 */
public final class GenerateTestPemMain {

    public static void main(String[] args) throws Exception {
        Path base =
                Path.of("src/test/resources/jwt").toAbsolutePath().normalize();
        Files.createDirectories(base);
        writePair(base.resolve("test-private.pem"), base.resolve("test-public.pem"));
        writePair(base.resolve("wrong-private.pem"), base.resolve("wrong-public.pem"));
        System.out.println("Written PEM files under " + base);
    }

    private static void writePair(Path privateOut, Path publicOut) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        Files.writeString(privateOut, toPkcs8Pem(kp.getPrivate().getEncoded()), StandardCharsets.UTF_8);
        Files.writeString(publicOut, toX509PublicPem(kp.getPublic().getEncoded()), StandardCharsets.UTF_8);
    }

    private static String toPkcs8Pem(byte[] der) {
        return wrapPem("PRIVATE KEY", Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der));
    }

    private static String toX509PublicPem(byte[] der) {
        return wrapPem("PUBLIC KEY", Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der));
    }

    private static String wrapPem(String type, String b64) {
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }
}
