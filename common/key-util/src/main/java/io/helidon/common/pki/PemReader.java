/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.common.pki;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Reads a PEM file and converts it into a list of DERs so that they are imported into a {@link java.security.KeyStore} easily.
 */
final class PemReader {
    private static final Logger LOGGER = Logger.getLogger(PemReader.class.getName());

    private static final Pattern CERT_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*CERTIFICATE[^-]*-+(?:\\s|\\r|\\n)+" // Header
                    + "([a-z0-9+/=\\r\\n]+)"                        // Base64 text
                    + "-+END\\s+.*CERTIFICATE[^-]*-+",              // Footer
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" // Header
                    + "([a-z0-9+/=\\r\\n]+)"                           // Base64 text
                    + "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+",              // Footer
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PUBLIC\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+"  // Header
                    + "([a-z0-9+/=\\r\\n\\s]+)"                        // Base64 text
                    + "-+END\\s+.*PUBLIC\\s+KEY[^-]*-+",               // Footer
            Pattern.CASE_INSENSITIVE);

    private PemReader() {
    }

    static PublicKey readPublicKey(InputStream input) {
        byte[] pkBytes = readPublicKeyBytes(input);

        X509EncodedKeySpec keySpec = generatePublicKeySpec(pkBytes);

        try {
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception ignore) {
            try {
                return KeyFactory.getInstance("DSA").generatePublic(keySpec);
            } catch (Exception ignore2) {
                try {
                    return KeyFactory.getInstance("EC").generatePublic(keySpec);
                } catch (Exception e) {
                    throw new PkiException("Failed to get public key. It is not RSA, DSA or EC.", e);
                }
            }
        }
    }

    static PrivateKey readPrivateKey(InputStream input, char[] password) {

        PrivateKeyInfo pkInfo = readPrivateKeyBytes(input);

        switch (pkInfo.type) {
        case "PKCS1-RSA":
            return rsaPrivateKey(Pkcs1Util.pkcs1RsaKeySpec(pkInfo.bytes));
        case "PKCS1-DSA":
            throw new UnsupportedOperationException("PKCS#1 DSA private key is not supported");
        case "PKCS1-EC":
            throw new UnsupportedOperationException("PKCS#1 EC private key is not supported");
        case "PKCS8":
        default:
            return pkcs8(generateKeySpec(pkInfo.bytes, password));
        }
    }

    private static PrivateKey pkcs8(KeySpec keySpec) {
        try {
            return rsaPrivateKey(keySpec);
        } catch (Exception rsaException) {
            try {
                return dsaPrivateKey(keySpec);
            } catch (Exception dsaException) {
                try {
                    return ecPrivateKey(keySpec);
                } catch (Exception ecException) {
                    PkiException e = new PkiException("Failed to get private key. It is not RSA, DSA or EC.");
                    e.addSuppressed(rsaException);
                    e.addSuppressed(dsaException);
                    e.addSuppressed(ecException);
                    throw e;
                }
            }
        }
    }

    private static PrivateKey ecPrivateKey(KeySpec keySpec) {
        try {
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);
        } catch (Exception e) {
            throw new PkiException("Failed to get EC private key", e);
        }
    }

    private static PrivateKey dsaPrivateKey(KeySpec keySpec) {
        try {
            return KeyFactory.getInstance("DSA").generatePrivate(keySpec);
        } catch (Exception e) {
            throw new PkiException("Failed to get DSA private key", e);
        }
    }

    private static PrivateKey rsaPrivateKey(KeySpec keySpec) {

        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            throw new PkiException("Failed to get RSA private key", e);
        }
    }

    static List<X509Certificate> readCertificates(InputStream certStream) {
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new PkiException("Failed to create certificate factory", e);
        }
        String content;
        try {
            content = readContent(certStream);
        } catch (IOException e) {
            throw new PkiException("Failed to read certificate input stream", e);
        } finally {
            safeClose(certStream);
        }

        List<X509Certificate> certs = new ArrayList<>();
        Matcher m = CERT_PATTERN.matcher(content);
        int start = 0;
        while (true) {
            if (!m.find(start)) {
                break;
            }

            byte[] base64 = m.group(1).getBytes(StandardCharsets.US_ASCII);
            byte[] der = Base64.getMimeDecoder().decode(base64);
            try {
                certs.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der)));
            } catch (Exception e) {
                throw new PkiException("Failed to read certificate from bytes", e);
            }

            start = m.end();
        }

        if (certs.isEmpty()) {
            throw new PkiException("Found no certificates in input stream");
        }

        return certs;
    }

    private static KeySpec generateKeySpec(byte[] keyBytes, char[] password) {
        if (password == null) {
            return new PKCS8EncodedKeySpec(keyBytes);
        }

        try {
            EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(keyBytes);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
            PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
            SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);

            Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());

            return encryptedPrivateKeyInfo.getKeySpec(cipher);
        } catch (Exception e) {
            throw new PkiException("Failed to create key spec for key", e);
        }
    }

    private static X509EncodedKeySpec generatePublicKeySpec(byte[] bytes) {
        return new X509EncodedKeySpec(bytes);
    }

    static PrivateKeyInfo readPrivateKeyBytes(InputStream in) {
        String content;
        try {
            content = readContent(in);
        } catch (IOException e) {
            throw new PkiException("Failed to read key input stream", e);
        } finally {
            safeClose(in);
        }

        Matcher m = KEY_PATTERN.matcher(content);
        if (!m.find()) {
            throw new PkiException("Could not find a PKCS#8 private key in input stream");
        }

        byte[] base64 = m.group(1).getBytes(StandardCharsets.US_ASCII);
        String type;
        if (content.startsWith("-----BEGIN PRIVATE KEY-----")
                || content.startsWith("-----BEGIN ENCRYPTED PRIVATE KEY-----")) {
            // in this case, we do not know the type, we must try all
            type = "PKCS8";
        } else if (content.startsWith("-----BEGIN RSA PRIVATE KEY-----")) {
            type = "PKCS1-RSA";
        } else if (content.startsWith("-----BEGIN DSA PRIVATE KEY-----")) {
            type = "PKCS1-DSA";
        } else if (content.startsWith("-----BEGIN EC PRIVATE KEY-----")) {
            type = "PKCS1-EC";
        } else {
            int firstEol = content.indexOf("\n");
            if (firstEol < 1) {
                throw new PkiException("Could not find a PKCS#8 private key in input stream");
            } else {
                throw new PkiException("Unsupported key type: " + content.substring(0, firstEol));
            }
        }
        return new PrivateKeyInfo(type, Base64.getMimeDecoder().decode(base64));
    }

    private static byte[] readPublicKeyBytes(InputStream in) {
        String content;
        try {
            content = readContent(in);
        } catch (IOException e) {
            throw new PkiException("Failed to read key input stream", e);
        } finally {
            safeClose(in);
        }

        Matcher m = PUBLIC_KEY_PATTERN.matcher(content);
        if (!m.find()) {
            throw new PkiException("Could not find a X509 public key in input stream");
        }

        byte[] base64 = m.group(1).getBytes(StandardCharsets.US_ASCII);
        return Base64.getMimeDecoder().decode(base64);
    }

    private static String readContent(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            while (true) {
                int ret = in.read(buf);
                if (ret < 0) {
                    break;
                }
                out.write(buf, 0, ret);
            }
            return out.toString(StandardCharsets.US_ASCII.name());
        } finally {
            safeClose(out);
        }
    }

    private static void safeClose(InputStream in) {
        try {
            in.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close a stream.", e);
        }
    }

    private static void safeClose(OutputStream out) {
        try {
            out.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close a stream.", e);
        }
    }

    private static final class PrivateKeyInfo {
        private final String type;
        private final byte[] bytes;

        PrivateKeyInfo(String type, byte[] bytes) {
            this.type = type;
            this.bytes = bytes;
        }
    }
}
