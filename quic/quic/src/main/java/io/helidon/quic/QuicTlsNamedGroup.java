/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.KeyAgreement;

enum QuicTlsNamedGroup {
    SECP256_R1(0x0017, "secp256r1", KeyType.ECDHE, ecParameterSpec("secp256r1"), 32),
    SECP384_R1(0x0018, "secp384r1", KeyType.ECDHE, ecParameterSpec("secp384r1"), 48),
    SECP521_R1(0x0019, "secp521r1", KeyType.ECDHE, ecParameterSpec("secp521r1"), 66),
    X25519(0x001D, "x25519", KeyType.XDH, NamedParameterSpec.X25519, 32),
    X448(0x001E, "x448", KeyType.XDH, NamedParameterSpec.X448, 56);

    private final int codePoint;
    private final String tlsName;
    private final KeyType keyType;
    private final AlgorithmParameterSpec parameterSpec;
    private final int coordinateLength;

    QuicTlsNamedGroup(int codePoint,
                      String tlsName,
                      KeyType keyType,
                      AlgorithmParameterSpec parameterSpec,
                      int coordinateLength) {
        this.codePoint = codePoint;
        this.tlsName = tlsName;
        this.keyType = keyType;
        this.parameterSpec = parameterSpec;
        this.coordinateLength = coordinateLength;
    }

    static QuicTlsNamedGroup forCodePoint(int codePoint) {
        for (QuicTlsNamedGroup value : values()) {
            if (value.codePoint == codePoint) {
                return value;
            }
        }
        throw new IllegalArgumentException(String.format("Unsupported TLS named group: 0x%04x", codePoint));
    }

    static boolean isSupportedTlsName(String tlsName) {
        for (QuicTlsNamedGroup value : values()) {
            if (value.tlsName.equalsIgnoreCase(tlsName)) {
                return true;
            }
        }
        return false;
    }

    static Optional<QuicTlsNamedGroup> forEcParameterSpec(ECParameterSpec parameterSpec) {
        Objects.requireNonNull(parameterSpec, "parameterSpec");
        for (QuicTlsNamedGroup value : values()) {
            if (value.parameterSpec instanceof ECParameterSpec ecParameterSpec && ecParameterSpec.equals(parameterSpec)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    int codePoint() {
        return codePoint;
    }

    String tlsName() {
        return tlsName;
    }

    KeyPair generateKeyPair(SecureRandom secureRandom) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(keyType.keyPairAlgorithm());
            keyPairGenerator.initialize(parameterSpec, secureRandom);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to generate a local " + tlsName + " key share", e);
        }
    }

    byte[] encodePublicKey(PublicKey publicKey) {
        return switch (keyType) {
            case ECDHE -> encodeEcPublicKey(publicKey);
            case XDH -> encodeXdhPublicKey(publicKey);
        };
    }

    PublicKey decodePublicKey(byte[] encodedKeyExchange) {
        return switch (keyType) {
            case ECDHE -> decodeEcPublicKey(encodedKeyExchange);
            case XDH -> decodeXdhPublicKey(encodedKeyExchange);
        };
    }

    byte[] deriveSharedSecret(PrivateKey privateKey, byte[] encodedPeerKeyExchange) {
        KeyAgreement keyAgreement;
        try {
            keyAgreement = KeyAgreement.getInstance(keyType.keyAgreementAlgorithm());
            keyAgreement.init(privateKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to initialize the local " + tlsName + " key share", e);
        }

        try {
            keyAgreement.doPhase(decodePublicKey(encodedPeerKeyExchange), true);
            return keyAgreement.generateSecret();
        } catch (InvalidKeyException e) {
            throw QuicTlsHandshakeMessages.illegalParameter("Invalid peer " + tlsName + " key share", e);
        } catch (ProviderException | IllegalStateException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to derive the " + tlsName + " shared secret", e);
        }
    }

    private static ECParameterSpec ecParameterSpec(String curveName) {
        try {
            AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance("EC");
            algorithmParameters.init(new ECGenParameterSpec(curveName));
            return algorithmParameters.getParameterSpec(ECParameterSpec.class);
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException | ProviderException e) {
            throw new IllegalStateException("Missing EC parameters for " + curveName, e);
        }
    }

    private static byte[] toUnsignedFixedLength(BigInteger value, int length) {
        byte[] encoded = trimLeadingZeros(value.toByteArray());
        if (encoded.length > length) {
            throw new IllegalArgumentException("Coordinate does not fit the required encoded length");
        }
        byte[] padded = new byte[length];
        System.arraycopy(encoded, 0, padded, padded.length - encoded.length, encoded.length);
        return padded;
    }

    private static byte[] trimLeadingZeros(byte[] bytes) {
        int index = 0;
        while (index < bytes.length - 1 && bytes[index] == 0) {
            index++;
        }
        return Arrays.copyOfRange(bytes, index, bytes.length);
    }

    private static void reverseInPlace(byte[] bytes) {
        for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
            byte current = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = current;
        }
    }

    private byte[] encodeEcPublicKey(PublicKey publicKey) {
        if (!(publicKey instanceof ECPublicKey ecPublicKey)) {
            throw new IllegalArgumentException("Expected EC public key for " + tlsName);
        }

        byte[] x = toUnsignedFixedLength(ecPublicKey.getW().getAffineX(), coordinateLength);
        byte[] y = toUnsignedFixedLength(ecPublicKey.getW().getAffineY(), coordinateLength);
        byte[] encodedPoint = new byte[1 + x.length + y.length];
        encodedPoint[0] = 0x04;
        System.arraycopy(x, 0, encodedPoint, 1, x.length);
        System.arraycopy(y, 0, encodedPoint, 1 + x.length, y.length);
        return encodedPoint;
    }

    private PublicKey decodeEcPublicKey(byte[] encodedKeyExchange) {
        if (encodedKeyExchange.length != 1 + (2 * coordinateLength) || encodedKeyExchange[0] != 0x04) {
            throw QuicTlsHandshakeMessages.illegalParameter("Malformed peer EC key share for " + tlsName);
        }

        byte[] x = Arrays.copyOfRange(encodedKeyExchange, 1, 1 + coordinateLength);
        byte[] y = Arrays.copyOfRange(encodedKeyExchange, 1 + coordinateLength, encodedKeyExchange.length);
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePublic(new ECPublicKeySpec(point, (ECParameterSpec) parameterSpec));
        } catch (InvalidKeySpecException e) {
            throw QuicTlsHandshakeMessages.illegalParameter("Invalid peer EC key share for " + tlsName, e);
        } catch (NoSuchAlgorithmException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to decode the peer EC key share for " + tlsName, e);
        }
    }

    private byte[] encodeXdhPublicKey(PublicKey publicKey) {
        if (!(publicKey instanceof XECPublicKey xecPublicKey)) {
            throw new IllegalArgumentException("Expected XDH public key for " + tlsName);
        }

        byte[] uBytes = trimLeadingZeros(xecPublicKey.getU().toByteArray());
        if (uBytes.length > coordinateLength) {
            throw new IllegalArgumentException("Encoded XDH key is too large for " + tlsName);
        }
        byte[] padded = new byte[coordinateLength];
        System.arraycopy(uBytes, 0, padded, padded.length - uBytes.length, uBytes.length);
        reverseInPlace(padded);
        return padded;
    }

    private PublicKey decodeXdhPublicKey(byte[] encodedKeyExchange) {
        if (encodedKeyExchange.length != coordinateLength) {
            throw QuicTlsHandshakeMessages.illegalParameter("Malformed peer XDH key share for " + tlsName);
        }

        byte[] uBytes = encodedKeyExchange.clone();
        reverseInPlace(uBytes);
        BigInteger u = new BigInteger(1, uBytes);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("XDH");
            return keyFactory.generatePublic(new XECPublicKeySpec((NamedParameterSpec) parameterSpec, u));
        } catch (InvalidKeySpecException e) {
            throw QuicTlsHandshakeMessages.illegalParameter("Invalid peer XDH key share for " + tlsName, e);
        } catch (NoSuchAlgorithmException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to decode the peer XDH key share for " + tlsName, e);
        }
    }

    private enum KeyType {
        ECDHE("EC", "ECDH"),
        XDH("XDH", "XDH");

        private final String keyPairAlgorithm;
        private final String keyAgreementAlgorithm;

        KeyType(String keyPairAlgorithm, String keyAgreementAlgorithm) {
            this.keyPairAlgorithm = keyPairAlgorithm;
            this.keyAgreementAlgorithm = keyAgreementAlgorithm;
        }

        String keyPairAlgorithm() {
            return keyPairAlgorithm;
        }

        String keyAgreementAlgorithm() {
            return keyAgreementAlgorithm;
        }
    }
}
