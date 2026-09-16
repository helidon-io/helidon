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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

enum QuicTlsSignatureScheme {
    // These signature-scheme names/code points mirror the public JSSE names exposed by SSLParameters so Helidon can
    // advertise certificate-verifier preferences without depending on any JDK-internal signature-scheme metadata.
    ECDSA_SECP256R1_SHA256(0x0403, "ecdsa_secp256r1_sha256", "SHA256withECDSA", null),
    ECDSA_SECP384R1_SHA384(0x0503, "ecdsa_secp384r1_sha384", "SHA384withECDSA", null),
    ECDSA_SECP521R1_SHA512(0x0603, "ecdsa_secp521r1_sha512", "SHA512withECDSA", null),
    ED25519(0x0807, "ed25519", "Ed25519", null),
    ED448(0x0808, "ed448", "Ed448", null),
    RSA_PSS_RSAE_SHA256(0x0804, "rsa_pss_rsae_sha256", "RSASSA-PSS",
                        pssSpec("SHA-256", MGF1ParameterSpec.SHA256, 32)),
    RSA_PSS_RSAE_SHA384(0x0805, "rsa_pss_rsae_sha384", "RSASSA-PSS",
                        pssSpec("SHA-384", MGF1ParameterSpec.SHA384, 48)),
    RSA_PSS_RSAE_SHA512(0x0806, "rsa_pss_rsae_sha512", "RSASSA-PSS",
                        pssSpec("SHA-512", MGF1ParameterSpec.SHA512, 64)),
    RSA_PSS_PSS_SHA256(0x0809, "rsa_pss_pss_sha256", "RSASSA-PSS",
                       pssSpec("SHA-256", MGF1ParameterSpec.SHA256, 32)),
    RSA_PSS_PSS_SHA384(0x080A, "rsa_pss_pss_sha384", "RSASSA-PSS",
                       pssSpec("SHA-384", MGF1ParameterSpec.SHA384, 48)),
    RSA_PSS_PSS_SHA512(0x080B, "rsa_pss_pss_sha512", "RSASSA-PSS",
                       pssSpec("SHA-512", MGF1ParameterSpec.SHA512, 64)),
    RSA_PKCS1_SHA256(0x0401, "rsa_pkcs1_sha256", "SHA256withRSA", null),
    RSA_PKCS1_SHA384(0x0501, "rsa_pkcs1_sha384", "SHA384withRSA", null),
    RSA_PKCS1_SHA512(0x0601, "rsa_pkcs1_sha512", "SHA512withRSA", null);

    private static final List<QuicTlsSignatureScheme> DEFAULT_CERTIFICATE_VERIFY_SCHEMES = List.of(
            ECDSA_SECP256R1_SHA256,
            ECDSA_SECP384R1_SHA384,
            ECDSA_SECP521R1_SHA512,
            ED25519,
            ED448,
            RSA_PSS_RSAE_SHA256,
            RSA_PSS_RSAE_SHA384,
            RSA_PSS_RSAE_SHA512,
            RSA_PSS_PSS_SHA256,
            RSA_PSS_PSS_SHA384,
            RSA_PSS_PSS_SHA512);
    private static final List<QuicTlsSignatureScheme> DEFAULT_CERTIFICATE_SIGNATURE_SCHEMES = List.of(values());
    // DER object identifier value octets, without the tag and length.
    private static final byte[] RSAE_ALGORITHM_OID =
            {(byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xF7,
                    (byte) 0x0D, (byte) 0x01, (byte) 0x01, (byte) 0x01};
    private static final byte[] RSASSA_PSS_OID =
            {(byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xF7,
                    (byte) 0x0D, (byte) 0x01, (byte) 0x01, (byte) 0x0A};
    private static final byte[] EC_ALGORITHM_OID =
            {(byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0xCE, (byte) 0x3D,
                    (byte) 0x02, (byte) 0x01};
    private static final byte[] ED25519_OID = {(byte) 0x2B, (byte) 0x65, (byte) 0x70};
    private static final byte[] ED448_OID = {(byte) 0x2B, (byte) 0x65, (byte) 0x71};

    private final int codePoint;
    private final String tlsName;
    private final String jcaSignatureAlgorithm;
    private final AlgorithmParameterSpec parameterSpec;

    QuicTlsSignatureScheme(int codePoint,
                           String tlsName,
                           String jcaSignatureAlgorithm,
                           AlgorithmParameterSpec parameterSpec) {
        this.codePoint = codePoint;
        this.tlsName = tlsName;
        this.jcaSignatureAlgorithm = jcaSignatureAlgorithm;
        this.parameterSpec = parameterSpec;
    }

    static QuicTlsSignatureScheme forTlsName(String tlsName) {
        for (QuicTlsSignatureScheme value : values()) {
            if (value.tlsName.equalsIgnoreCase(tlsName)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported TLS signature scheme: " + tlsName);
    }

    static QuicTlsSignatureScheme forCodePoint(int codePoint) {
        for (QuicTlsSignatureScheme value : values()) {
            if (value.codePoint == codePoint) {
                return value;
            }
        }
        throw new IllegalArgumentException(String.format("Unsupported TLS signature scheme: 0x%04x", codePoint));
    }

    static Optional<QuicTlsSignatureScheme> supportedCodePoint(int codePoint) {
        for (QuicTlsSignatureScheme value : values()) {
            if (value.codePoint == codePoint) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    static List<QuicTlsSignatureScheme> certificateVerifySchemes(String[] configuredSchemes) {
        if (configuredSchemes == null || configuredSchemes.length == 0) {
            return DEFAULT_CERTIFICATE_VERIFY_SCHEMES;
        }

        Set<QuicTlsSignatureScheme> schemes = new LinkedHashSet<>();
        for (String configuredScheme : configuredSchemes) {
            QuicTlsSignatureScheme candidate = forTlsName(configuredScheme);
            if (candidate.certificateVerify()) {
                schemes.add(candidate);
            }
        }
        if (schemes.isEmpty()) {
            throw new IllegalArgumentException("No supported TLS 1.3 CertificateVerify signature schemes are enabled");
        }
        return List.copyOf(schemes);
    }

    static List<QuicTlsSignatureScheme> certificateSignatureSchemes(String[] configuredSchemes) {
        if (configuredSchemes == null || configuredSchemes.length == 0) {
            return DEFAULT_CERTIFICATE_SIGNATURE_SCHEMES;
        }

        Set<QuicTlsSignatureScheme> schemes = new LinkedHashSet<>();
        for (String configuredScheme : configuredSchemes) {
            schemes.add(forTlsName(configuredScheme));
        }
        return List.copyOf(schemes);
    }

    static List<QuicTlsSignatureScheme> decodeCertificateVerifyVector(ByteBuffer buffer,
                                                                      String fieldName,
                                                                      String messageName)
            throws QuicTransportException {
        return decodeVector(buffer, fieldName, messageName, true);
    }

    static List<QuicTlsSignatureScheme> decodeCertificateSignatureVector(ByteBuffer buffer,
                                                                         String fieldName,
                                                                         String messageName)
            throws QuicTransportException {
        return decodeVector(buffer, fieldName, messageName, false);
    }

    static ByteBuffer encodeCertificateVerifyVector(List<QuicTlsSignatureScheme> signatureSchemes) {
        List<QuicTlsSignatureScheme> schemes = List.copyOf(signatureSchemes);
        for (QuicTlsSignatureScheme scheme : schemes) {
            if (!scheme.certificateVerify()) {
                throw new IllegalArgumentException(
                        "TLS signature scheme is not valid for TLS 1.3 CertificateVerify: " + scheme.tlsName());
            }
        }
        return encodeVector(schemes);
    }

    static ByteBuffer encodeCertificateSignatureVector(List<QuicTlsSignatureScheme> signatureSchemes) {
        return encodeVector(signatureSchemes);
    }

    static boolean supportsCertificateChain(X509Certificate[] certificateChain,
                                            List<QuicTlsSignatureScheme> signatureSchemes) {
        if (certificateChain == null || certificateChain.length == 0 || signatureSchemes == null) {
            return false;
        }
        for (X509Certificate certificate : certificateChain) {
            if (certificate == null) {
                return false;
            }
        }

        // A final certificate is exempt only when this chain establishes that it is genuinely self-signed. When the
        // trust anchor is omitted, the final supplied certificate's signature algorithm still has to be negotiated.
        int certificatesToValidate = certificateChain.length;
        X509Certificate finalCertificate = certificateChain[certificateChain.length - 1];
        try {
            PublicKey finalPublicKey = finalCertificate.getPublicKey();
            if (finalPublicKey != null
                    && finalCertificate.getSubjectX500Principal().equals(finalCertificate.getIssuerX500Principal())) {
                finalCertificate.verify(finalPublicKey);
                certificatesToValidate--;
            }
        } catch (GeneralSecurityException | ProviderException e) {
            // A self-issued certificate that cannot verify its own signature is not an established trust anchor.
        }

        for (int i = 0; i < certificatesToValidate; i++) {
            X509Certificate certificate = certificateChain[i];
            PublicKey issuerPublicKey = null;
            if (i + 1 < certificateChain.length) {
                try {
                    issuerPublicKey = certificateChain[i + 1].getPublicKey();
                } catch (ProviderException e) {
                    return false;
                }
                if (issuerPublicKey == null) {
                    return false;
                }
            }

            boolean supported = false;
            for (QuicTlsSignatureScheme signatureScheme : signatureSchemes) {
                if (signatureScheme != null
                        && signatureScheme.supportsCertificateSignature(certificate, issuerPublicKey)) {
                    supported = true;
                    break;
                }
            }
            if (!supported) {
                return false;
            }
        }
        return true;
    }

    int codePoint() {
        return codePoint;
    }

    String tlsName() {
        return tlsName;
    }

    String jcaSignatureAlgorithm() {
        return jcaSignatureAlgorithm;
    }

    boolean certificateVerify() {
        // RFC 8446 section 4.2.3 retains these code points only for signatures in certificates. TLS 1.3 RSA
        // CertificateVerify messages use RSASSA-PSS; RFC 9963 legacy client signatures have distinct code points.
        return switch (this) {
            case RSA_PKCS1_SHA256, RSA_PKCS1_SHA384, RSA_PKCS1_SHA512 -> false;
            default -> true;
        };
    }

    String keyType() {
        return switch (this) {
            case ECDSA_SECP256R1_SHA256, ECDSA_SECP384R1_SHA384, ECDSA_SECP521R1_SHA512 -> "EC";
            case ED25519, ED448 -> "EdDSA";
            case RSA_PSS_PSS_SHA256, RSA_PSS_PSS_SHA384, RSA_PSS_PSS_SHA512 -> "RSASSA-PSS";
            case RSA_PSS_RSAE_SHA256, RSA_PSS_RSAE_SHA384, RSA_PSS_RSAE_SHA512,
                 RSA_PKCS1_SHA256, RSA_PKCS1_SHA384, RSA_PKCS1_SHA512 -> "RSA";
        };
    }

    QuicTlsNamedGroup requiredCertificateGroup() {
        return switch (this) {
            case ECDSA_SECP256R1_SHA256 -> QuicTlsNamedGroup.SECP256_R1;
            case ECDSA_SECP384R1_SHA384 -> QuicTlsNamedGroup.SECP384_R1;
            case ECDSA_SECP521R1_SHA512 -> QuicTlsNamedGroup.SECP521_R1;
            default -> null;
        };
    }

    boolean supports(PrivateKey privateKey, PublicKey publicKey) {
        try {
            Signature signer = newSignature();
            signer.initSign(privateKey);
            configure(signer);
            Signature verifier = newSignature();
            verifier.initVerify(publicKey);
            configure(verifier);
            return true;
        } catch (InvalidKeyException e) {
            return false;
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError(
                    "Failed to probe the local " + tlsName + " signature implementation", e);
        }
    }

    Signature newSigner(PrivateKey privateKey) {
        try {
            Signature signature = newSignature();
            signature.initSign(privateKey);
            configure(signature);
            return signature;
        } catch (InvalidKeyException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to initialize the local " + tlsName + " signer", e);
        }
    }

    Signature newVerifier(PublicKey publicKey) {
        try {
            Signature signature = newSignature();
            signature.initVerify(publicKey);
            configure(signature);
            return signature;
        } catch (InvalidKeyException e) {
            throw QuicTlsHandshakeMessages.decryptError("Invalid public key for peer " + tlsName + " signature", e);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to initialize the " + tlsName + " verifier", e);
        }
    }

    private static List<QuicTlsSignatureScheme> decodeVector(ByteBuffer buffer,
                                                              String fieldName,
                                                              String messageName,
                                                              boolean certificateVerify)
            throws QuicTransportException {
        ByteBuffer encoded = QuicTlsCodecSupport.readVector(buffer,
                                                            QuicTlsCodecSupport.UINT16_LENGTH,
                                                            fieldName,
                                                            messageName);
        QuicTlsCodecSupport.ensureConsumed(buffer, messageName);
        if (encoded.remaining() == 0 || (encoded.remaining() & 0x1) != 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + messageName + " message: " + fieldName);
        }
        Set<QuicTlsSignatureScheme> signatureSchemes = new LinkedHashSet<>();
        while (encoded.hasRemaining()) {
            // TLS signature_algorithms negotiation lists can include values we do not implement yet; keep only the
            // actionable, ordered, de-duplicated subset and let certificate selection decide whether overlap remains.
            supportedCodePoint(encoded.getShort() & 0xFFFF)
                    .filter(signatureScheme -> !certificateVerify || signatureScheme.certificateVerify())
                    .ifPresent(signatureSchemes::add);
        }
        return List.copyOf(signatureSchemes);
    }

    private static ByteBuffer encodeVector(List<QuicTlsSignatureScheme> signatureSchemes) {
        List<QuicTlsSignatureScheme> schemes = List.copyOf(signatureSchemes);
        if (schemes.isEmpty()) {
            throw new IllegalArgumentException("At least one TLS signature scheme is required");
        }

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT16_LENGTH
                                                         + (QuicTlsCodecSupport.UINT16_LENGTH * schemes.size()));
        encoded.putShort((short) (QuicTlsCodecSupport.UINT16_LENGTH * schemes.size()));
        for (QuicTlsSignatureScheme signatureScheme : schemes) {
            encoded.putShort((short) signatureScheme.codePoint());
        }
        return encoded.flip();
    }

    private static SubjectPublicKeyAlgorithm subjectPublicKeyAlgorithm(PublicKey publicKey) {
        byte[] encoded;
        try {
            encoded = publicKey.getEncoded();
        } catch (ProviderException e) {
            return null;
        }
        if (encoded == null) {
            return null;
        }

        ByteBuffer subjectPublicKeyInfo = derValue(ByteBuffer.wrap(encoded), 0x30);
        if (subjectPublicKeyInfo == null) {
            return null;
        }
        ByteBuffer algorithmIdentifier = derValue(subjectPublicKeyInfo, 0x30);
        if (algorithmIdentifier == null) {
            return null;
        }
        ByteBuffer objectIdentifier = derValue(algorithmIdentifier, 0x06);
        if (objectIdentifier == null) {
            return null;
        }
        byte[] encodedObjectIdentifier = new byte[objectIdentifier.remaining()];
        objectIdentifier.get(encodedObjectIdentifier);
        byte[] encodedParameters = null;
        if (algorithmIdentifier.hasRemaining()) {
            encodedParameters = new byte[algorithmIdentifier.remaining()];
            algorithmIdentifier.get(encodedParameters);
        }
        return new SubjectPublicKeyAlgorithm(encodedObjectIdentifier, encodedParameters);
    }

    private static ByteBuffer derValue(ByteBuffer source, int expectedTag) {
        if (source.remaining() < 2 || (source.get() & 0xFF) != expectedTag) {
            return null;
        }

        int firstLengthByte = source.get() & 0xFF;
        int length;
        if ((firstLengthByte & 0x80) == 0) {
            length = firstLengthByte;
        } else {
            int lengthBytes = firstLengthByte & 0x7F;
            if (lengthBytes == 0 || lengthBytes > Integer.BYTES || source.remaining() < lengthBytes) {
                return null;
            }
            length = 0;
            for (int i = 0; i < lengthBytes; i++) {
                int next = source.get() & 0xFF;
                if (length > (Integer.MAX_VALUE >>> Byte.SIZE)) {
                    return null;
                }
                length = (length << Byte.SIZE) | next;
            }
        }
        if (length < 0 || source.remaining() < length) {
            return null;
        }

        ByteBuffer value = source.slice(source.position(), length).asReadOnlyBuffer();
        source.position(source.position() + length);
        return value;
    }

    private static boolean sameDigest(String first, String second) {
        return first.replace("-", "").equalsIgnoreCase(second.replace("-", ""));
    }

    private static PSSParameterSpec pssSpec(String digestAlgorithm,
                                            MGF1ParameterSpec mgf1ParameterSpec,
                                            int saltLength) {
        return new PSSParameterSpec(digestAlgorithm, "MGF1", mgf1ParameterSpec, saltLength, 1);
    }

    private boolean supportsCertificateSignature(X509Certificate certificate, PublicKey issuerPublicKey) {
        String expectedSignatureAlgorithmOid = switch (this) {
            case ECDSA_SECP256R1_SHA256 -> "1.2.840.10045.4.3.2";
            case ECDSA_SECP384R1_SHA384 -> "1.2.840.10045.4.3.3";
            case ECDSA_SECP521R1_SHA512 -> "1.2.840.10045.4.3.4";
            case ED25519 -> "1.3.101.112";
            case ED448 -> "1.3.101.113";
            case RSA_PSS_RSAE_SHA256, RSA_PSS_RSAE_SHA384, RSA_PSS_RSAE_SHA512,
                 RSA_PSS_PSS_SHA256, RSA_PSS_PSS_SHA384, RSA_PSS_PSS_SHA512 -> "1.2.840.113549.1.1.10";
            case RSA_PKCS1_SHA256 -> "1.2.840.113549.1.1.11";
            case RSA_PKCS1_SHA384 -> "1.2.840.113549.1.1.12";
            case RSA_PKCS1_SHA512 -> "1.2.840.113549.1.1.13";
        };
        try {
            if (!expectedSignatureAlgorithmOid.equals(certificate.getSigAlgOID())) {
                return false;
            }
        } catch (ProviderException e) {
            return false;
        }

        if (issuerPublicKey == null
                && (this == RSA_PSS_RSAE_SHA256
                        || this == RSA_PSS_RSAE_SHA384
                        || this == RSA_PSS_RSAE_SHA512
                        || this == RSA_PSS_PSS_SHA256
                        || this == RSA_PSS_PSS_SHA384
                        || this == RSA_PSS_PSS_SHA512)) {
            // Both RSA-PSS scheme families use the same certificate signature OID. Without the issuer key, its
            // rsaEncryption versus RSASSA-PSS AlgorithmIdentifier cannot be established, so this chain is only a
            // candidate for the RFC 8446 fallback rather than a policy-compatible preference.
            return false;
        }

        SubjectPublicKeyAlgorithm issuerKeyAlgorithm = null;
        if (issuerPublicKey != null) {
            issuerKeyAlgorithm = subjectPublicKeyAlgorithm(issuerPublicKey);
            byte[] expectedIssuerAlgorithmOid = switch (this) {
                case ECDSA_SECP256R1_SHA256, ECDSA_SECP384R1_SHA384, ECDSA_SECP521R1_SHA512 -> EC_ALGORITHM_OID;
                case ED25519 -> ED25519_OID;
                case ED448 -> ED448_OID;
                case RSA_PSS_RSAE_SHA256, RSA_PSS_RSAE_SHA384, RSA_PSS_RSAE_SHA512,
                     RSA_PKCS1_SHA256, RSA_PKCS1_SHA384, RSA_PKCS1_SHA512 -> RSAE_ALGORITHM_OID;
                case RSA_PSS_PSS_SHA256, RSA_PSS_PSS_SHA384, RSA_PSS_PSS_SHA512 -> RSASSA_PSS_OID;
            };
            if (issuerKeyAlgorithm == null
                    || !Arrays.equals(expectedIssuerAlgorithmOid, issuerKeyAlgorithm.objectIdentifier())) {
                return false;
            }

            QuicTlsNamedGroup requiredGroup = requiredCertificateGroup();
            if (requiredGroup != null) {
                if (!(issuerPublicKey instanceof ECPublicKey ecPublicKey)) {
                    return false;
                }
                ECParameterSpec parameterSpec = ecPublicKey.getParams();
                if (parameterSpec == null
                        || QuicTlsNamedGroup.forEcParameterSpec(parameterSpec)
                                .filter(requiredGroup::equals)
                                .isEmpty()) {
                    return false;
                }
            }
        }

        if (!(parameterSpec instanceof PSSParameterSpec expectedPssSpec)) {
            return true;
        }

        byte[] encodedParameters;
        try {
            encodedParameters = certificate.getSigAlgParams();
        } catch (ProviderException e) {
            return false;
        }
        if (encodedParameters == null) {
            return false;
        }
        if (issuerKeyAlgorithm != null
                && issuerKeyAlgorithm.parameters() != null
                && (this == RSA_PSS_PSS_SHA256 || this == RSA_PSS_PSS_SHA384 || this == RSA_PSS_PSS_SHA512)
                && !Arrays.equals(issuerKeyAlgorithm.parameters(), encodedParameters)) {
            return false;
        }

        try {
            AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance("RSASSA-PSS");
            algorithmParameters.init(encodedParameters);
            PSSParameterSpec actualPssSpec = algorithmParameters.getParameterSpec(PSSParameterSpec.class);
            if (!sameDigest(expectedPssSpec.getDigestAlgorithm(), actualPssSpec.getDigestAlgorithm())
                    || !"MGF1".equalsIgnoreCase(actualPssSpec.getMGFAlgorithm())
                    || !(actualPssSpec.getMGFParameters() instanceof MGF1ParameterSpec actualMgfSpec)
                    || !(expectedPssSpec.getMGFParameters() instanceof MGF1ParameterSpec expectedMgfSpec)) {
                return false;
            }
            return sameDigest(expectedMgfSpec.getDigestAlgorithm(), actualMgfSpec.getDigestAlgorithm())
                    && expectedPssSpec.getSaltLength() == actualPssSpec.getSaltLength()
                    && expectedPssSpec.getTrailerField() == actualPssSpec.getTrailerField();
        } catch (IOException | NoSuchAlgorithmException | InvalidParameterSpecException | ProviderException e) {
            return false;
        }
    }

    private Signature newSignature() {
        try {
            return Signature.getInstance(jcaSignatureAlgorithm);
        } catch (NoSuchAlgorithmException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Missing local " + tlsName + " signature implementation", e);
        }
    }

    private void configure(Signature signature) {
        try {
            if (parameterSpec != null) {
                signature.setParameter(parameterSpec);
            }
        } catch (InvalidAlgorithmParameterException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError(
                    "Failed to configure the local " + tlsName + " signature implementation", e);
        }
    }

    private record SubjectPublicKeyAlgorithm(byte[] objectIdentifier, byte[] parameters) {
    }
}
