/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.examples.security.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Poorly inspired from {@code org.glassfish.jersey.client.authentication.DigestAuthenticator}.
 */
class DigestAuthenticator {

    private static final char[] HEX_ARRAY = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final Pattern KEY_VALUE_PAIR_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*(\"([^\"]+)\"|(\\w+))\\s*,?\\s*");

    private final SecureRandom random = new SecureRandom();

    /**
     * Respond to the challenge.
     *
     * @param challenge response challenge
     * @param uri       request uri
     * @param method    request method
     * @param username  username
     * @param password  password
     * @return authorization header value or {@code null}
     */
    String authorization(String challenge, String uri, String method, String username, String password) {
        DigestScheme ds = parseDigestScheme(challenge);
        return ds != null ? header(ds, uri, method, username, password) : null;
    }

    private String header(DigestScheme ds, String uri, String method, String username, String password) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("Digest ");
        append(sb, "username", username);
        append(sb, "realm", ds.realm());
        append(sb, "nonce", ds.nonce());
        append(sb, "opaque", ds.opaque());
        append(sb, "algorithm", ds.algorithm(), false);
        append(sb, "qop", ds.qop(), false);
        append(sb, "uri", uri);

        String ha1;
        if (ds.algorithm().equals("MD5_SESS")) {
            ha1 = md5(md5(username, ds.realm(), password));
        } else {
            ha1 = md5(username, ds.realm(), password);
        }

        String ha2 = md5(method, uri);
        String response;
        if (ds.qop() == null) {
            response = md5(ha1, ds.nonce(), ha2);
        } else {
            String cnonce = randomBytes(); // client nonce
            append(sb, "cnonce", cnonce);
            String nc = String.format("%08x", ds.nc.incrementAndGet()); // counter
            append(sb, "nc", nc, false);
            response = md5(ha1, ds.nonce(), nc, cnonce, ds.qop(), ha2);
        }
        append(sb, "response", response);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, String value, boolean useQuote) {
        if (value == null) {
            return;
        }
        if (sb.length() > 0) {
            if (sb.charAt(sb.length() - 1) != ' ') {
                sb.append(',');
            }
        }
        sb.append(key);
        sb.append('=');
        if (useQuote) {
            sb.append('"');
        }
        sb.append(value);
        if (useQuote) {
            sb.append('"');
        }
    }

    private static void append(StringBuilder sb, String key, String value) {
        append(sb, key, value, true);
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String md5(String... tokens) {
        StringBuilder sb = new StringBuilder(100);
        for (String token : tokens) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(token);
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex.getMessage());
        }
        md.update(sb.toString().getBytes(StandardCharsets.UTF_8), 0, sb.length());
        byte[] md5hash = md.digest();
        return bytesToHex(md5hash);
    }


    private String randomBytes() {
        byte[] bytes = new byte[4];
        random.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private record DigestScheme(String realm,
                                String nonce,
                                String opaque,
                                String qop,
                                String algorithm,
                                boolean stale,
                                AtomicInteger nc) {
    }

    static DigestScheme parseDigestScheme(String header) {
        String[] parts = header.trim().split("\\s+", 2);
        if (parts.length != 2) {
            return null;
        }
        if (!"digest".equals(parts[0].toLowerCase(Locale.ROOT))) {
            return null;
        }
        String realm = null;
        String nonce = null;
        String opaque = null;
        String qop = null;
        String algorithm = null;
        boolean stale = false;
        Matcher match = KEY_VALUE_PAIR_PATTERN.matcher(parts[1]);
        while (match.find()) {
            // expect 4 groups (key)=("(val)" | (val))
            int nbGroups = match.groupCount();
            if (nbGroups != 4) {
                continue;
            }
            String key = match.group(1);
            String valNoQuotes = match.group(3);
            String valQuotes = match.group(4);
            String val = (valNoQuotes == null) ? valQuotes : valNoQuotes;
            if ("qop".equals(key)) {
                qop = val.contains("auth") ? "auth" : null;
            } else if ("realm".equals(key)) {
                realm = val;
            } else if ("nonce".equals(key)) {
                nonce = val;
            } else if ("opaque".equals(key)) {
                opaque = val;
            } else if ("stale".equals(key)) {
                stale = Boolean.parseBoolean(val);
            } else if ("algorithm".equals(key)) {
                if (val == null || val.isBlank()) {
                    continue;
                }
                val = val.trim();
                if (val.contains("MD5-sess") || val.contains("MD5-sess".toLowerCase(Locale.ROOT))) {
                    algorithm = "MD5-sess";
                } else {
                    algorithm = "MD5";
                }
            }
        }
        return new DigestScheme(realm, nonce, opaque, qop, algorithm, stale, new AtomicInteger(0));
    }
}
