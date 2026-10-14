package de.kryptofix.passwordmanager;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verschluesselter lokaler Tresor.
 * Primitive: ausschliesslich Standard-JCA (PBKDF2-HMAC-SHA256, AES-256-GCM) - keine Eigenentwicklung.
 * Dateiformat: "KFX1" | iterations(int) | salt(16) | iv(12) | ciphertext+tag.
 * Die ersten 24 Bytes (Magic, Iterationen, Salt) sind als AAD authentifiziert.
 */
final class Vault {
    static final class Entry {
        String id = java.util.UUID.randomUUID().toString();
        String title = "", user = "", url = "", pw = "", notes = "";
        long modified = System.currentTimeMillis();
    }

    static final int ITERATIONS = 600000;
    private static final byte[] MAGIC = {'K', 'F', 'X', '1'};
    static final List<Entry> entries = new ArrayList<>();
    private static SecretKeySpec key;
    private static byte[] salt;
    private static int iter;
    private static final SecureRandom RNG = new SecureRandom();

    private Vault() {}

    static File file(Context c) { return new File(c.getFilesDir(), "vault.kfx"); }
    static boolean exists(Context c) { return file(c).exists(); }
    static boolean unlocked() { return key != null; }

    static byte[] rand(int n) { byte[] b = new byte[n]; RNG.nextBytes(b); return b; }

    private static byte[] derive(char[] pw, byte[] s, int it) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw, s, it, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static void create(Context c, char[] pw) throws Exception {
        byte[] s = rand(16);
        key = new SecretKeySpec(derive(pw, s, ITERATIONS), "AES");
        salt = s;
        iter = ITERATIONS;
        entries.clear();
        save(c);
    }

    /** Neues Hauptpasswort setzen (frischer Salt, aktuelle Iterationszahl). */
    static void rekey(Context c, char[] pw) throws Exception {
        byte[] s = rand(16);
        SecretKeySpec k = new SecretKeySpec(derive(pw, s, ITERATIONS), "AES");
        key = k;
        salt = s;
        iter = ITERATIONS;
        save(c);
    }

    static boolean unlock(Context c, char[] pw) {
        try {
            byte[] d = readAll(file(c));
            ByteBuffer bb = ByteBuffer.wrap(d);
            byte[] m = new byte[4];
            bb.get(m);
            if (!java.util.Arrays.equals(m, MAGIC)) return false;
            int it = bb.getInt();
            if (it < 10000 || it > 5000000) return false;
            byte[] s = new byte[16];
            bb.get(s);
            byte[] iv = new byte[12];
            bb.get(iv);
            byte[] ct = new byte[bb.remaining()];
            bb.get(ct);
            SecretKeySpec k = new SecretKeySpec(derive(pw, s, it), "AES");
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
            ci.updateAAD(d, 0, 24);
            byte[] pt = ci.doFinal(ct);
            JSONArray arr = new JSONArray(new String(pt, StandardCharsets.UTF_8));
            java.util.Arrays.fill(pt, (byte) 0);
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.optString("id", e.id);
                e.title = o.optString("t");
                e.user = o.optString("u");
                e.url = o.optString("l");
                e.pw = o.optString("p");
                e.notes = o.optString("n");
                e.modified = o.optLong("m");
                list.add(e);
            }
            entries.clear();
            entries.addAll(list);
            key = k;
            salt = s;
            iter = it;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void save(Context c) throws Exception {
        if (key == null) throw new IllegalStateException("locked");
        JSONArray arr = new JSONArray();
        for (Entry e : entries) {
            JSONObject o = new JSONObject();
            o.put("id", e.id);
            o.put("t", e.title);
            o.put("u", e.user);
            o.put("l", e.url);
            o.put("p", e.pw);
            o.put("n", e.notes);
            o.put("m", e.modified);
            arr.put(o);
        }
        byte[] pt = arr.toString().getBytes(StandardCharsets.UTF_8);
        byte[] header = ByteBuffer.allocate(24).put(MAGIC).putInt(iter).put(salt).array();
        byte[] iv = rand(12);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        ci.updateAAD(header);
        byte[] ct = ci.doFinal(pt);
        java.util.Arrays.fill(pt, (byte) 0);

        File tmp = new File(c.getFilesDir(), "vault.kfx.tmp");
        FileOutputStream fos = new FileOutputStream(tmp);
        try {
            fos.write(header);
            fos.write(iv);
            fos.write(ct);
            fos.getFD().sync();
        } finally {
            fos.close();
        }
        if (!tmp.renameTo(file(c))) {
            file(c).delete();
            if (!tmp.renameTo(file(c))) throw new java.io.IOException("rename failed");
        }
    }

    static void lock() {
        key = null;
        salt = null;
        entries.clear();
    }

    static void wipe(Context c) {
        lock();
        file(c).delete();
        new File(c.getFilesDir(), "vault.kfx.tmp").delete();
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
            return b;
        } finally {
            in.close();
        }
    }

    // ---- Hilfsfunktionen ----

    /** 0 = sehr schwach ... 4 = stark */
    static int strength(String p) {
        if (p == null || p.isEmpty()) return 0;
        int classes = 0;
        boolean lo = false, up = false, di = false, sy = false;
        for (char ch : p.toCharArray()) {
            if (Character.isLowerCase(ch)) lo = true;
            else if (Character.isUpperCase(ch)) up = true;
            else if (Character.isDigit(ch)) di = true;
            else sy = true;
        }
        if (lo) classes++;
        if (up) classes++;
        if (di) classes++;
        if (sy) classes++;
        int n = p.length();
        int s;
        if (n < 8) s = 0;
        else if (n < 12) s = 1;
        else if (n < 16) s = 2;
        else s = 3;
        if (classes >= 3 && n >= 12) s++;
        if (classes <= 1 && s > 1) s = 1;
        return Math.min(s, 4);
    }

    static String generate(int len) {
        String lo = "abcdefghijkmnopqrstuvwxyz", up = "ABCDEFGHJKLMNPQRSTUVWXYZ", di = "23456789", sy = "!@#$%&*?-_+=";
        String all = lo + up + di + sy;
        char[] out = new char[len];
        String[] sets = {lo, up, di, sy};
        for (int i = 0; i < len; i++) {
            String src = i < 4 ? sets[i] : all;
            out[i] = src.charAt(RNG.nextInt(src.length()));
        }
        for (int i = len - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            char t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return new String(out);
    }
}
