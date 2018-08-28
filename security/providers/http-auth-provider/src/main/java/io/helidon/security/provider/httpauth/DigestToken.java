/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.provider.httpauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Digest token parsing and processing.
 */
class DigestToken {
    private static final Logger LOGGER = Logger.getLogger(DigestToken.class.getName());
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private String username;
    private String realm;
    private String uri;
    private HttpDigest.Algorithm algorithm;
    private String response;
    private String opaque;
    private HttpDigest.Qop qop;
    private String nc;
    private String cnonce;
    private String method;
    private String nonce;

    static DigestToken fromAuthorizationHeader(String header, String method) {
        Map<String, String> values = new HashMap<>();
        String[] strings = header.split(",");
        for (String string : strings) {
            int eq = string.indexOf('=');
            if (eq > 0) {
                String trimmed = string.trim(); //remove spaces around comma
                eq = trimmed.indexOf('=');
                String key = trimmed.substring(0, eq).trim();
                String value = unquote(trimmed.substring(eq + 1).trim());
                values.put(key, value);
            } else {
                LOGGER.finest(() -> "Unrecognized digest header value: " + string);
            }
        }

        // all values parsed
        DigestToken dt = new DigestToken();
        dt.username = values.get("username");
        dt.realm = values.get("realm");
        dt.uri = values.get("uri");
        dt.algorithm = HttpDigest.Algorithm.fromString(values.get("algorithm"));
        dt.response = values.get("response");
        dt.opaque = values.get("opaque");
        dt.qop = HttpDigest.Qop.fromString(values.get("qop"));
        dt.method = method.toUpperCase();
        dt.nc = values.get("nc");
        dt.cnonce = values.get("cnonce");
        dt.nonce = values.get("nonce");

        StringBuilder validationMessage = new StringBuilder();
        if (null == dt.username) {
            validationMessage.append("username is null, ");
        }
        if (null == dt.realm) {
            validationMessage.append("realm is null, ");
        }
        if (null == dt.uri) {
            validationMessage.append("uri is null, ");
        }
        if (null == dt.response) {
            validationMessage.append("response is null, ");
        }
        if (null == dt.opaque) {
            validationMessage.append("opaque is null, ");
        }
        if (null == dt.method) {
            validationMessage.append("method is null, ");
        }
        if (null == dt.nonce) {
            validationMessage.append("nonce is null, ");
        }

        if (dt.qop != HttpDigest.Qop.NONE) {
            if (null == dt.nc) {
                validationMessage.append("nc is null, ");
            }
            if (null == dt.cnonce) {
                validationMessage.append("cnonce is null, ");
            }
        }

        if (validationMessage.length() != 0) {
            throw new HttpAuthException("Validation of digest header failed: " + validationMessage
                    .substring(0, validationMessage.length() - 2));
        }

        return dt;
    }

    private static String unquote(String string) {
        if (string.startsWith("\"") && string.endsWith("\"")) {
            return string.substring(1, string.length() - 1);
        }
        return string;
    }

    private static String md5(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm should be supported", e);
        }
        return bytesToHex(digest.digest(bytes));
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    String getRealm() {
        return realm;
    }

    void setRealm(String realm) {
        this.realm = realm;
    }

    String getUri() {
        return uri;
    }

    void setUri(String uri) {
        this.uri = uri;
    }

    HttpDigest.Algorithm getAlgorithm() {
        return algorithm;
    }

    void setAlgorithm(HttpDigest.Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    String getResponse() {
        return response;
    }

    void setResponse(String response) {
        this.response = response;
    }

    String getOpaque() {
        return opaque;
    }

    void setOpaque(String opaque) {
        this.opaque = opaque;
    }

    HttpDigest.Qop getQop() {
        return qop;
    }

    void setQop(HttpDigest.Qop qop) {
        this.qop = qop;
    }

    String getNc() {
        return nc;
    }

    void setNc(String nc) {
        this.nc = nc;
    }

    String getCnonce() {
        return cnonce;
    }

    void setCnonce(String cnonce) {
        this.cnonce = cnonce;
    }

    String getNonce() {
        return nonce;
    }

    void setNonce(String nonce) {
        this.nonce = nonce;
    }

    String getMethod() {
        return method;
    }

    void setMethod(String method) {
        this.method = method;
    }

    boolean validateLogin(char[] password) {
        String digest = digest(password);

        return digest.equals(response);
    }

    String digest(char[] password) {
        String ha1 = ha1(password);
        String ha2 = ha2();

        switch (qop) {
        case NONE:
            return md5(ha1 + ":" + nonce + ":" + ha2);
        case AUTH:
            return md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop.getQop() + ":" + ha2);
        default:
            throw new IllegalArgumentException("Only auth or no QOP are supported");
        }

    }

    private String ha2() {
        return md5(a2());
    }

    private String ha1(char[] password) {
        switch (algorithm) {
        case MD5:
            return md5(a1(password));
        //        case MD5_SESS:
        //            return md5(md5(a1(password) + ":" + nonce + ":" + cnonce));
        default:
            throw new IllegalArgumentException("Only MD5 algorithm is supported");
        }

    }

    private String a1(char[] password) {
        switch (algorithm) {
        case MD5:
            //A1       = unq(username-value) ":" unq(realm-value) ":" passwd
            return username + ":" + realm + ":" + new String(password);
        default:
            throw new IllegalArgumentException("Only MD5 algorithm is supported");
        }

    }

    private String a2() {
        switch (qop) {
        case NONE:
        case AUTH:
            //A2       = Method ":" digest-uri-value
            return method + ":" + uri;
        default:
            throw new IllegalArgumentException("Only auth or no QOP are supported");
        }
    }

    @Override
    public String toString() {
        return "DigestToken{"
                + "username='" + username + '\''
                + ", realm='" + realm + '\''
                + ", uri='" + uri + '\''
                + ", algorithm=" + algorithm
                + ", response='" + response + '\''
                + ", opaque='" + opaque + '\''
                + ", qop=" + qop
                + ", nc='" + nc + '\''
                + ", cnonce='" + cnonce + '\''
                + ", method='" + method + '\''
                + ", nonce='" + nonce + '\''
                + '}';
    }
}
