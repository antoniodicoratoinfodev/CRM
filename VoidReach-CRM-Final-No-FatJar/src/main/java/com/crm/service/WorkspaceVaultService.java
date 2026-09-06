package com.crm.service;

import com.crm.model.UserAccount;
import com.crm.repository.AtomicPropertiesStore;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** AES-256-GCM data keys, independently wrapped by a password and an offline recovery key. */
public final class WorkspaceVaultService {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, byte[]> KEYS = new ConcurrentHashMap<>();
    private static final Set<String> ENABLED = ConcurrentHashMap.newKeySet();
    private WorkspaceVaultService() { }
    public static final class Setup implements AutoCloseable {
        private final byte[] master;
        private final String config, recoveryKey;
        private Setup(byte[] master, String config, String recoveryKey) { this.master = master; this.config = config; this.recoveryKey = recoveryKey; }
        public String recoveryKey() { return recoveryKey; }
        public String config() { return config; }
        @Override public void close() { Arrays.fill(master, (byte)0); }
        @Override public String toString() { return "Vault setup [redacted]"; }
    }
    public static Setup prepare(UserAccount user, String password) {
        if (user.isVaultEnabled()) throw new IllegalArgumentException("Local protection is already enabled.");
        byte[] master = random(32); String recovery = newRecoveryKey();
        return new Setup(master, wrap(master, password, user.getId() + ":password") + ";" + wrap(master, recovery, user.getId() + ":recovery"), recovery);
    }
    public static void activate(UserAccount user, Setup setup) {
        user.setVaultConfig(setup.config()); user.setRecoveryVerifier(verifier(setup.recoveryKey()));
        register(user.getId(), setup.master);
    }
    public static void unlock(UserAccount user, String password) {
        if (!user.isVaultEnabled()) return;
        byte[] master = unwrap(parts(user)[0], password, user.getId() + ":password");
        try { register(user.getId(), master); } finally { Arrays.fill(master, (byte)0); }
    }
    public static boolean isEnabled(String userId) { return ENABLED.contains(userId); }
    public static void markEnabled(String userId) { ENABLED.add(userId); }
    public static synchronized void lock(String userId) { byte[] key = KEYS.remove(userId); if (key != null) Arrays.fill(key, (byte)0); }
    private static synchronized void register(String userId, byte[] master) {
        if (master.length != 32) throw new IllegalArgumentException("Invalid workspace key.");
        byte[] previous = KEYS.put(userId, master.clone()); if (previous != null) Arrays.fill(previous, (byte)0); ENABLED.add(userId);
    }
    public static void changePassword(UserAccount user, String password) {
        if (!user.isVaultEnabled()) return;
        byte[] key = requireKey(user.getId());
        try { user.setVaultConfig(wrap(key, password, user.getId() + ":password") + ";" + parts(user)[1]); }
        finally { Arrays.fill(key, (byte)0); }
    }
    public static void replaceRecoveryKey(UserAccount user, String recovery) {
        if (user.isVaultEnabled()) {
            byte[] key = requireKey(user.getId());
            try { user.setVaultConfig(parts(user)[0] + ";" + wrap(key, recovery, user.getId() + ":recovery")); }
            finally { Arrays.fill(key, (byte)0); }
        }
        user.setRecoveryVerifier(verifier(recovery));
    }
    public static void recover(UserAccount user, String recovery, String newPassword) {
        if (!user.isVaultEnabled()) return;
        String wrapped = parts(user)[1];
        if (wrapped.isBlank()) throw new IllegalArgumentException("This recovery key was already used.");
        byte[] master = unwrap(wrapped, recovery.trim(), user.getId() + ":recovery");
        try { user.setVaultConfig(wrap(master, newPassword, user.getId() + ":password") + ";"); register(user.getId(), master); }
        finally { Arrays.fill(master, (byte)0); }
    }
    private static String[] parts(UserAccount user) {
        String[] parts = user.getVaultConfig().split(";", -1);
        if (parts.length != 2) throw new IllegalStateException("The local protection settings are damaged. Keep your account backups.");
        return parts;
    }
    public static String newRecoveryKey() { return Base64.getUrlEncoder().withoutPadding().encodeToString(random(32)); }
    public static String verifier(String key) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(key.trim().getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException failure) { throw new IllegalStateException("Recovery key verification is unavailable.", failure); }
    }
    public static boolean matchesRecovery(UserAccount user, String key) {
        return key != null && !user.getRecoveryVerifier().isBlank() && MessageDigest.isEqual(
                user.getRecoveryVerifier().getBytes(StandardCharsets.UTF_8), verifier(key).getBytes(StandardCharsets.UTF_8));
    }
    public static boolean encrypted(Properties properties) {
        String version = properties.getProperty("vault.version");
        if ((version != null || properties.containsKey("vault.payload")) && !"1".equals(version))
            throw new IllegalArgumentException("Unsupported or damaged encryption envelope.");
        return "1".equals(version);
    }
    public static Properties seal(Properties plaintext, String userId) {
        if (!isEnabled(userId)) return plaintext;
        byte[] key = requireKey(userId);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); plaintext.store(bytes, "VoidReach workspace");
            byte[] nonce = random(12), plain = bytes.toByteArray();
            byte[] encrypted;
            try { encrypted = cipher(Cipher.ENCRYPT_MODE, key, nonce, plain, "workspace:" + userId); }
            finally { Arrays.fill(plain, (byte)0); }
            Properties envelope = new Properties();
            envelope.setProperty("schema.version", plaintext.getProperty("schema.version", "2"));
            envelope.setProperty("file.type", plaintext.getProperty("file.type", "voidreach.crm-data"));
            envelope.setProperty("vault.version", "1"); envelope.setProperty("vault.owner", userId);
            envelope.setProperty("vault.nonce", encode(nonce)); envelope.setProperty("vault.payload", encode(encrypted));
            return envelope;
        } catch (IOException failure) { throw new IllegalStateException("Workspace encryption failed.", failure); }
        finally { Arrays.fill(key, (byte)0); }
    }
    public static Properties unseal(Properties envelope, String expectedUser) {
        if (!encrypted(envelope)) return envelope;
        String owner = envelope.getProperty("vault.owner");
        if (owner == null || expectedUser != null && !owner.equals(expectedUser)) throw new IllegalArgumentException("This encrypted file belongs to another profile.");
        markEnabled(owner);
        byte[] key = requireKey(owner), plaintext;
        try { plaintext = cipher(Cipher.DECRYPT_MODE, key, decode(envelope.getProperty("vault.nonce")),
                decode(envelope.getProperty("vault.payload")), "workspace:" + owner); }
        finally { Arrays.fill(key, (byte)0); }
        try {
            Properties properties = new Properties(); properties.load(new ByteArrayInputStream(plaintext)); return properties;
        } catch (IOException failure) { throw new IllegalStateException("The decrypted workspace is invalid.", failure); }
        finally { Arrays.fill(plaintext, (byte)0); }
    }
    public static void encryptExisting(Path file, String userId) {
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("Protection does not follow file symlinks. Keep the file inside the account's data folder.");
        if (!Files.isRegularFile(file)) return;
        try {
            Properties plaintext = new Properties(); try (InputStream input = Files.newInputStream(file)) { plaintext.load(input); }
            if (encrypted(plaintext)) { unseal(plaintext, userId); return; }
            if (!isEnabled(userId)) throw new IllegalStateException("Activate local protection before migration.");
            Properties encrypted = seal(plaintext, userId);
            if (!unseal(encrypted, userId).equals(plaintext)) throw new IllegalStateException("Encryption verification failed; file unchanged.");
            AtomicPropertiesStore.storeReencrypted(file, encrypted);
        } catch (IOException failure) { throw new IllegalStateException("An existing file could not be encrypted. Retry local protection setup.", failure); }
    }
    private static synchronized byte[] requireKey(String userId) {
        byte[] key = KEYS.get(userId);
        if (key == null) throw new IllegalStateException("Unlock the originating profile with its password before reading encrypted data.");
        return key.clone();
    }
    private static String wrap(byte[] master, String password, String purpose) {
        byte[] salt = random(16), nonce = random(12), key = derive(password, salt);
        try { return encode(salt) + ":" + encode(nonce) + ":" + encode(cipher(Cipher.ENCRYPT_MODE, key, nonce, master, purpose)); }
        finally { Arrays.fill(key, (byte)0); }
    }
    private static byte[] unwrap(String envelope, String password, String purpose) {
        String[] parts = envelope.split(":", -1);
        if (parts.length != 3) throw new IllegalStateException("Invalid encrypted key.");
        byte[] key = derive(password, decode(parts[0]));
        try { return cipher(Cipher.DECRYPT_MODE, key, decode(parts[1]), decode(parts[2]), purpose); }
        finally { Arrays.fill(key, (byte)0); }
    }
    private static byte[] derive(String password, byte[] salt) {
        if (salt.length != 16 || password == null || password.length() > 4096) throw new IllegalArgumentException("Invalid password or salt.");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException failure) { throw new IllegalStateException("Key derivation is unavailable.", failure); }
        finally { spec.clearPassword(); }
    }
    private static byte[] cipher(int mode, byte[] key, byte[] nonce, byte[] input, String purpose) {
        if (nonce.length != 12) throw new IllegalArgumentException("Invalid encryption nonce.");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("VoidReach:v1:" + purpose).getBytes(StandardCharsets.UTF_8)); return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) { throw new IllegalArgumentException("The password/key is incorrect or the encrypted data is damaged.", failure); }
    }
    private static byte[] random(int length) { byte[] bytes = new byte[length]; RANDOM.nextBytes(bytes); return bytes; }
    private static String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private static byte[] decode(String text) { if (text == null) throw new IllegalArgumentException("Missing encrypted data."); return Base64.getDecoder().decode(text); }
}
