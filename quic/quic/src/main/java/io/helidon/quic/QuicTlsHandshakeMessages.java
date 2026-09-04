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
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.PKIXReason;
import java.security.cert.X509Certificate;
import java.util.IdentityHashMap;

import javax.net.ssl.SSLHandshakeException;

import static io.helidon.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static io.helidon.quic.QuicTLSEngine.KeySpace.INITIAL;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ONE_RTT;

final class QuicTlsHandshakeMessages {
    static final int HEADER_LENGTH = 4;
    static final int QUIC_TRANSPORT_PARAMETERS_EXTENSION = QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS;

    static final int CLIENT_HELLO = 1;
    static final int SERVER_HELLO = 2;
    static final int NEW_SESSION_TICKET = 4;
    static final int ENCRYPTED_EXTENSIONS = 8;
    static final int CERTIFICATE = 11;
    static final int CERTIFICATE_REQUEST = 13;
    static final int CERTIFICATE_VERIFY = 15;
    static final int FINISHED = 20;

    private static final long BASE_CRYPTO_ERROR = QuicTransportErrors.CRYPTO_ERROR.from();
    private static final int TLS_ALERT_UNEXPECTED_MESSAGE = 10;
    private static final int TLS_ALERT_HANDSHAKE_FAILURE = 40;
    private static final int TLS_ALERT_BAD_CERTIFICATE = 42;
    private static final int TLS_ALERT_CERTIFICATE_REVOKED = 44;
    private static final int TLS_ALERT_CERTIFICATE_EXPIRED = 45;
    private static final int TLS_ALERT_CERTIFICATE_UNKNOWN = 46;
    private static final int TLS_ALERT_ILLEGAL_PARAMETER = 47;
    private static final int TLS_ALERT_UNKNOWN_CA = 48;
    private static final int TLS_ALERT_DECODE_ERROR = 50;
    private static final int TLS_ALERT_DECRYPT_ERROR = 51;
    private static final int TLS_ALERT_MISSING_EXTENSION = 109;
    private static final int TLS_ALERT_UNRECOGNIZED_NAME = 112;
    private static final int TLS_ALERT_NO_APPLICATION_PROTOCOL = 120;
    private static final int TLS_ALERT_CERTIFICATE_REQUIRED = 116;

    private QuicTlsHandshakeMessages() {
    }

    static int messageType(ByteBuffer message) {
        requireHeader(message);
        return message.get(message.position()) & 0xFF;
    }

    static int messageBodyLength(ByteBuffer message) {
        requireHeader(message);
        int offset = message.position();
        return ((message.get(offset + 1) & 0xFF) << 16)
                | ((message.get(offset + 2) & 0xFF) << 8)
                | (message.get(offset + 3) & 0xFF);
    }

    static QuicTLSEngine.KeySpace expectedKeySpace(int messageType) {
        return switch (messageType) {
            case CLIENT_HELLO, SERVER_HELLO -> INITIAL;
            case ENCRYPTED_EXTENSIONS, CERTIFICATE_REQUEST, CERTIFICATE, CERTIFICATE_VERIFY, FINISHED -> HANDSHAKE;
            case NEW_SESSION_TICKET -> ONE_RTT;
            default -> null;
        };
    }

    static boolean isNewSessionTicket(ByteBuffer message) {
        return messageType(message) == NEW_SESSION_TICKET;
    }

    static QuicTransportException unexpectedMessage(String detail) {
        return alert("Unexpected message", TLS_ALERT_UNEXPECTED_MESSAGE, new SSLHandshakeException(detail));
    }

    static QuicTransportException handshakeFailure(String detail) {
        return alert(detail, TLS_ALERT_HANDSHAKE_FAILURE, new SSLHandshakeException(detail));
    }

    static QuicTransportException handshakeFailure(String detail, Throwable cause) {
        return alert(detail, TLS_ALERT_HANDSHAKE_FAILURE, cause);
    }

    static QuicTransportException badCertificate(String detail) {
        return new QuicTransportException(detail, 0, BASE_CRYPTO_ERROR + TLS_ALERT_BAD_CERTIFICATE);
    }

    static QuicTransportException badCertificate(String detail, Throwable cause) {
        return alert(detail, TLS_ALERT_BAD_CERTIFICATE, cause);
    }

    static PublicKey peerCertificatePublicKey(X509Certificate certificate) {
        try {
            PublicKey publicKey = certificate.getPublicKey();
            if (publicKey == null) {
                throw badCertificate("Peer certificate does not provide a public key");
            }
            return publicKey;
        } catch (ProviderException e) {
            throw internalError("Failed to read the peer certificate public key", e);
        }
    }

    static String peerCertificateAuthType(X509Certificate certificate) {
        PublicKey publicKey = peerCertificatePublicKey(certificate);
        String algorithm;
        try {
            algorithm = publicKey.getAlgorithm();
        } catch (ProviderException e) {
            throw internalError("Failed to read the peer certificate public key algorithm", e);
        }
        return switch (algorithm) {
            case "RSA", "DSA", "EC", "RSASSA-PSS" -> algorithm;
            default -> "UNKNOWN";
        };
    }

    static QuicTransportException certificateFailure(String detail, Throwable cause) {
        boolean expired = false;
        boolean badCertificate = false;
        boolean unknownCa = false;
        IdentityHashMap<Throwable, Boolean> visited = new IdentityHashMap<>();

        for (Throwable current = cause;
                current != null && visited.put(current, Boolean.TRUE) == null;
                current = current.getCause()) {
            if (current instanceof CertificateRevokedException) {
                return alert(detail, TLS_ALERT_CERTIFICATE_REVOKED, cause);
            }
            if (current instanceof CertificateExpiredException
                    || current instanceof CertificateNotYetValidException) {
                expired = true;
            } else if (current instanceof CertificateParsingException
                    || current instanceof CertificateEncodingException
                    || current instanceof SignatureException) {
                badCertificate = true;
            } else if (current instanceof CertPathBuilderException) {
                unknownCa = true;
            }

            if (current instanceof CertPathValidatorException validatorException) {
                CertPathValidatorException.Reason reason = validatorException.getReason();
                if (reason == CertPathValidatorException.BasicReason.REVOKED) {
                    return alert(detail, TLS_ALERT_CERTIFICATE_REVOKED, cause);
                }
                if (reason == CertPathValidatorException.BasicReason.EXPIRED
                        || reason == CertPathValidatorException.BasicReason.NOT_YET_VALID) {
                    expired = true;
                } else if (reason == CertPathValidatorException.BasicReason.INVALID_SIGNATURE) {
                    badCertificate = true;
                } else if (reason == PKIXReason.NO_TRUST_ANCHOR) {
                    unknownCa = true;
                }
            }
        }

        if (expired) {
            return alert(detail, TLS_ALERT_CERTIFICATE_EXPIRED, cause);
        }
        if (badCertificate) {
            return alert(detail, TLS_ALERT_BAD_CERTIFICATE, cause);
        }
        if (unknownCa) {
            return alert(detail, TLS_ALERT_UNKNOWN_CA, cause);
        }
        return alert(detail, TLS_ALERT_CERTIFICATE_UNKNOWN, cause);
    }

    static QuicTransportException decodeError(String detail) {
        return alert(detail, TLS_ALERT_DECODE_ERROR, new SSLHandshakeException(detail));
    }

    static QuicTransportException missingExtension(String detail) {
        return alert(detail, TLS_ALERT_MISSING_EXTENSION, new SSLHandshakeException(detail));
    }

    static QuicTransportException illegalParameter(String detail) {
        return alert(detail, TLS_ALERT_ILLEGAL_PARAMETER, new SSLHandshakeException(detail));
    }

    static QuicTransportException illegalParameter(String detail, Throwable cause) {
        return alert(detail, TLS_ALERT_ILLEGAL_PARAMETER, cause);
    }

    static QuicTransportException decryptError(String detail) {
        return alert(detail, TLS_ALERT_DECRYPT_ERROR, new SSLHandshakeException(detail));
    }

    static QuicTransportException decryptError(String detail, Throwable cause) {
        return alert(detail, TLS_ALERT_DECRYPT_ERROR, cause);
    }

    static QuicTransportException internalError(String detail, Throwable cause) {
        return new QuicTransportException(detail,
                                          0,
                                          QuicTransportErrors.INTERNAL_ERROR.code(),
                                          cause);
    }

    static QuicTransportException certificateRequired(String detail) {
        return alert(detail, TLS_ALERT_CERTIFICATE_REQUIRED, new SSLHandshakeException(detail));
    }

    static QuicTransportException unrecognizedName(String detail, Throwable cause) {
        return alert(detail, TLS_ALERT_UNRECOGNIZED_NAME, cause);
    }

    static QuicTransportException noApplicationProtocol(String detail) {
        return alert(detail, TLS_ALERT_NO_APPLICATION_PROTOCOL, new SSLHandshakeException(detail));
    }

    private static void requireHeader(ByteBuffer message) {
        if (message.remaining() < HEADER_LENGTH) {
            throw new IllegalArgumentException("Incomplete TLS handshake message header");
        }
    }

    private static QuicTransportException alert(String detail, int alert, Throwable cause) {
        return new QuicTransportException(detail, 0, BASE_CRYPTO_ERROR + alert, cause);
    }
}
