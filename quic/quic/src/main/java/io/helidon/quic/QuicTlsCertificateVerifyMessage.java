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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;

final class QuicTlsCertificateVerifyMessage {
    // TLS 1.3 prepends 64 space characters plus the fixed server context string before the transcript hash so the
    // certificate signature cannot be replayed across protocol versions or message types.
    private static final byte[] SERVER_SIGNATURE_CONTEXT =
            signatureContext("TLS 1.3, server CertificateVerify");
    // The client context string uses the same 64-space prefix but a different label so mutual-TLS signatures stay
    // distinct from the server-authentication path even when the transcript hash is otherwise identical.
    private static final byte[] CLIENT_SIGNATURE_CONTEXT =
            signatureContext("TLS 1.3, client CertificateVerify");

    private final QuicTlsSignatureScheme signatureScheme;
    private final byte[] signature;

    private QuicTlsCertificateVerifyMessage(QuicTlsSignatureScheme signatureScheme, byte[] signature) {
        this.signatureScheme = Objects.requireNonNull(signatureScheme, "signatureScheme");
        this.signature = Objects.requireNonNull(signature, "signature").clone();
    }

    static QuicTlsCertificateVerifyMessage create(QuicTlsSignatureScheme signatureScheme, byte[] signature) {
        return new QuicTlsCertificateVerifyMessage(requireCertificateVerifyScheme(signatureScheme), signature);
    }

    static QuicTlsCertificateVerifyMessage decode(ByteBuffer message) throws QuicTransportException {
        ByteBuffer body =
                QuicTlsCodecSupport.handshakeBody(message, QuicTlsHandshakeMessages.CERTIFICATE_VERIFY, "CertificateVerify");
        int codePoint = QuicTlsCodecSupport.readUnsigned(body,
                                                         QuicTlsCodecSupport.UINT16_LENGTH,
                                                         "algorithm",
                                                         "CertificateVerify");
        QuicTlsSignatureScheme signatureScheme;
        try {
            signatureScheme = QuicTlsSignatureScheme.forCodePoint(codePoint);
        } catch (IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.handshakeFailure("Unsupported TLS signature scheme in CertificateVerify", e);
        }
        if (!signatureScheme.certificateVerify()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "TLS signature scheme is not valid for TLS 1.3 CertificateVerify: " + signatureScheme.tlsName());
        }
        byte[] signature = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(body,
                                                                                   QuicTlsCodecSupport.UINT16_LENGTH,
                                                                                   "signature",
                                                                                   "CertificateVerify"));
        if (signature.length == 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed CertificateVerify message: empty signature");
        }
        QuicTlsCodecSupport.ensureConsumed(body, "CertificateVerify");
        return new QuicTlsCertificateVerifyMessage(signatureScheme, signature);
    }

    static QuicTlsCertificateVerifyMessage sign(QuicTlsSignatureScheme signatureScheme,
                                                PrivateKey privateKey,
                                                byte[] transcriptHash,
                                                boolean clientSignature) {
        try {
            QuicTlsSignatureScheme selectedScheme = requireCertificateVerifyScheme(signatureScheme);
            Signature signer = selectedScheme
                    .newSigner(Objects.requireNonNull(privateKey, "privateKey"));
            signer.update(signedContent(transcriptHash, clientSignature));
            return new QuicTlsCertificateVerifyMessage(selectedScheme, signer.sign());
        } catch (SignatureException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to create the local CertificateVerify signature", e);
        }
    }

    QuicTlsSignatureScheme signatureScheme() {
        return signatureScheme;
    }

    byte[] signature() {
        return signature.clone();
    }

    boolean verify(PublicKey publicKey, byte[] transcriptHash, boolean clientSignature) {
        try {
            Signature verifier = signatureScheme.newVerifier(Objects.requireNonNull(publicKey, "publicKey"));
            verifier.update(signedContent(transcriptHash, clientSignature));
            if (!verifier.verify(signature)) {
                throw QuicTlsHandshakeMessages.decryptError("Invalid peer CertificateVerify signature");
            }
            return true;
        } catch (SignatureException e) {
            throw QuicTlsHandshakeMessages.decryptError("Invalid peer CertificateVerify signature", e);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to verify the peer CertificateVerify signature", e);
        }
    }

    ByteBuffer encode() {
        int bodyLength = QuicTlsCodecSupport.UINT16_LENGTH
                + QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH, signature.length, "signature");
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + bodyLength);
        QuicTlsCodecSupport.putHandshakeHeader(encoded, QuicTlsHandshakeMessages.CERTIFICATE_VERIFY, bodyLength);
        QuicTlsCodecSupport.putUnsigned(encoded, QuicTlsCodecSupport.UINT16_LENGTH, signatureScheme.codePoint());
        QuicTlsCodecSupport.putVector(encoded, QuicTlsCodecSupport.UINT16_LENGTH, signature, "signature");
        return encoded.flip();
    }

    private static byte[] signedContent(byte[] transcriptHash, boolean clientSignature) {
        byte[] hash = Objects.requireNonNull(transcriptHash, "transcriptHash").clone();
        byte[] context = clientSignature ? CLIENT_SIGNATURE_CONTEXT : SERVER_SIGNATURE_CONTEXT;
        byte[] content = Arrays.copyOf(context, context.length + hash.length);
        System.arraycopy(hash, 0, content, context.length, hash.length);
        return content;
    }

    private static byte[] signatureContext(String label) {
        byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
        byte[] context = new byte[64 + labelBytes.length + 1];
        Arrays.fill(context, 0, 64, (byte) 0x20);
        System.arraycopy(labelBytes, 0, context, 64, labelBytes.length);
        return context;
    }

    private static QuicTlsSignatureScheme requireCertificateVerifyScheme(QuicTlsSignatureScheme signatureScheme) {
        QuicTlsSignatureScheme scheme = Objects.requireNonNull(signatureScheme, "signatureScheme");
        if (!scheme.certificateVerify()) {
            throw new IllegalArgumentException(
                    "TLS signature scheme is not valid for TLS 1.3 CertificateVerify: " + scheme.tlsName());
        }
        return scheme;
    }
}
