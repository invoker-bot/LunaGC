package emu.grasscutter.utils;

import emu.grasscutter.Grasscutter;
import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class RSADecryptionUtil {
    private static PrivateKey sdkPrivateKey;
    private static PrivateKey authPrivateKey;
    // 与 patch/server_public_key.bin 配对的 2048-bit 私钥（国服客户端 ma-cn-passport 登录加密用）
    private static PrivateKey passportPrivateKey;
    // LunaGC: 与 AccountPlatNative.dll @0x6C9480 内置 1024-bit 护照公钥配对的私钥。
    // 国服 7.0.0 客户端的 loginByPassword 账号/密码密文恒为 128 字节（1024-bit RSA），
    // 上述 2048-bit 密钥无法解密；已用同长度新密钥替换 DLL 内的公钥，此处放其私钥半。
    private static PrivateKey passport1024PrivateKey;

    static {
        sdkPrivateKey = loadKey("/keys/private_key.der", "private_key.der");
        authPrivateKey = loadKey("/keys/auth_private-key.der", "auth_private-key.der");
        passportPrivateKey = loadKey("/keys/SigningKey.der", "SigningKey.der");
        passport1024PrivateKey = loadKey("/keys/passport_1024.der", "passport_1024.der");
    }

    private static PrivateKey loadKey(String resourcePath, String name) {
        try {
            byte[] keyBytes = FileUtils.readResource(resourcePath);
            if (keyBytes == null || keyBytes.length == 0) {
                Grasscutter.getLogger().warn("Key not found: " + name);
                return null;
            }
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Failed to load key " + name + ": " + e.getMessage());
            return null;
        }
    }

    public static String decrypt(String encryptedBase64) throws Exception {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
            throw new IllegalArgumentException("Encrypted data is null or empty");
        }

        byte[] encryptedBytes;
        try {
            encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
        } catch (Exception e) {
            throw new Exception("Failed to base64-decode input", e);
        }

        Grasscutter.getLogger().debug("RSA decrypt attempt: input base64 len=" + encryptedBase64.length() + " bytes=" + encryptedBytes.length);

        if (sdkPrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, sdkPrivateKey, "RSA/ECB/PKCS1Padding", "sdk+PKCS1");
            if (result != null) return result;
        }

        if (sdkPrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, sdkPrivateKey, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", "sdk+OAEP");
            if (result != null) return result;
        }

        if (authPrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, authPrivateKey, "RSA/ECB/PKCS1Padding", "auth+PKCS1");
            if (result != null) return result;
        }

        if (authPrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, authPrivateKey, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", "auth+OAEP");
            if (result != null) return result;
        }

        // patch 注入的 server_public_key.bin 加密的载荷（国服 ma-cn-passport 登录）
        if (passportPrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, passportPrivateKey, "RSA/ECB/PKCS1Padding", "passport+PKCS1");
            if (result != null) return result;
        }

        if (passportPrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, passportPrivateKey, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", "passport+OAEP");
            if (result != null) return result;
        }

        // LunaGC: AccountPlatNative.dll 内 1024-bit 护照公钥加密的账号/密码密文
        if (passport1024PrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, passport1024PrivateKey, "RSA/ECB/PKCS1Padding", "passport1024+PKCS1");
            if (result != null) return result;
        }

        if (passport1024PrivateKey != null) {
            String result = tryDecrypt(encryptedBytes, passport1024PrivateKey, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", "passport1024+OAEP");
            if (result != null) return result;
        }

        String plaintext = new String(encryptedBytes, StandardCharsets.UTF_8);
        if (isPrintable(plaintext)) {
            Grasscutter.getLogger().info("RSA decrypt: using plaintext fallback, value=" + plaintext);
            return plaintext;
        }

        Grasscutter.getLogger().error("RSA decrypt: all methods failed for input (base64 len=" + encryptedBase64.length() + ")");
        throw new Exception("RSA decryption failed: no matching key or padding");
    }

    private static String tryDecrypt(byte[] data, PrivateKey key, String transformation, String label) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(data);
            String result = new String(decrypted, StandardCharsets.UTF_8);
            Grasscutter.getLogger().info("RSA decrypt succeeded with " + label + " -> [" + result + "]");
            return result;
        } catch (Exception e) {
            Grasscutter.getLogger().debug("RSA decrypt failed with " + label + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean isPrintable(String s) {
        if (s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) return false;
        }
        return true;
    }
}
