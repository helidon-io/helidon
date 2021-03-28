package io.helidon.integrations.oci.connect;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.httpsign.HttpSignatureException;
import io.helidon.security.providers.httpsign.SignedHeadersConfig;

/**
 * Class wrapping signature and fields needed to build and validate it.
 */
class OciHttpSignature {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger(OciHttpSignature.class.getName());
    private static final List<String> DEFAULT_HEADERS = List.of("date");
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final String ALGORITHM = "rsa-sha256";

    private final String keyId;
    private final List<String> headers;
    private String base64Signature;

    private byte[] signatureBytes;

    OciHttpSignature(String keyId, List<String> headers) {
        this.keyId = keyId;
        this.headers = headers;
    }

    static OciHttpSignature sign(SignatureRequest request) {
        OciHttpSignature signature = new OciHttpSignature(request.keyId,
                                                          request.headersToSign);

        signature.signatureBytes = signature.signRsaSha256(request.env,
                                                           request.privateKey,
                                                           request.newHeaders);

        signature.base64Signature = Base64.getEncoder().encodeToString(signature.signatureBytes);

        return signature;
    }

    String toSignatureHeader() {
        return "keyId=\"" + keyId + "\","
                + "algorithm=\"" + ALGORITHM + "\","
                + "headers=\"" + String.join(" ", headers) + "\","
                + "signature=\"" + base64Signature + "\"";
    }

    String getKeyId() {
        return keyId;
    }

    String getAlgorithm() {
        return ALGORITHM;
    }

    List<String> getHeaders() {
        return Collections.unmodifiableList(headers);
    }

    String getBase64Signature() {
        return base64Signature;
    }

    Optional<String> validate() {
        List<String> problems = new ArrayList<>();

        if (null == keyId) {
            problems.add("keyId is a mandatory signature header component");
        }
        if (null == base64Signature) {
            problems.add("signature is a mandatory signature header component");
        }

        try {
            this.signatureBytes = Base64.getDecoder().decode(base64Signature);
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Cannot get bytes from base64: " + base64Signature, e);
            problems.add("cannot get bytes from base64 encoded signature: " + e.getMessage());
        }

        if (problems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("HttpSignature is not valid. Problems: " + String.join(", ", problems));
    }

    private byte[] signRsaSha256(SecurityEnvironment env, RSAPrivateKey privateKey, Map<String, List<String>> newHeaders) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(getBytesToSign(env, newHeaders));
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new HttpSignatureException(e);
        }
    }

    private byte[] getBytesToSign(SecurityEnvironment env, Map<String, List<String>> newHeaders) {
        return getSignedString(newHeaders, env).getBytes(StandardCharsets.UTF_8);
    }

    String getSignedString(Map<String, List<String>> newHeaders, SecurityEnvironment env) {
        Map<String, List<String>> requestHeaders = env.headers();
        List<String> linesToSign = new LinkedList<>();

        for (String header : this.headers) {
            if ("(request-target)".equals(header)) {
                //special case
                linesToSign.add(header
                                        + ": " + env.method().toLowerCase()
                                        + " " + env.path().orElse("/"));
            } else {
                List<String> headerValues = requestHeaders.get(header);
                if (null == headerValues && null == newHeaders) {
                    // we do not support creation of new headers, just throw an exception
                    throw new HttpSignatureException("Header " + header + " is required for signature, yet not defined in "
                                                             + "request");
                }
                if (null == headerValues) {
                    // there are two headers we understand and may want to add to request
                    if ("date".equalsIgnoreCase(header)) {
                        String date = ZonedDateTime.now(ZoneId.of("GMT")).format(DATE_FORMATTER);
                        headerValues = List.of(date);
                        newHeaders.put("date", headerValues);

                        LOGGER.finest(() -> "Added date header to request: " + date);
                    } else if ("host".equalsIgnoreCase(header)) {
                        URI uri = env.targetUri();

                        String host = uri.getHost() + ":" + uri.getPort();
                        headerValues = List.of(host);
                        newHeaders.put("host", headerValues);

                        LOGGER.finest(() -> "Added host header to request: " + host);
                    } else {
                        throw new HttpSignatureException("Header " + header + " is required for signature, yet not defined in "
                                                                 + "request");
                    }
                }

                linesToSign.add(header + ": " + String.join(" ", headerValues));
            }
        }

        // 2.3.  Signature String Construction
        // If value is not the last value then append an ASCII newline `\n`.
        String toSign = String.join("\n", linesToSign);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Data to sign: " + toSign);
        }

        return toSign;
    }

    public static class SignatureRequest {
        private final SecurityEnvironment env;
        private final Map<String, List<String>> newHeaders;
        private final String keyId;
        private final List<String> headersToSign;
        private final RSAPrivateKey privateKey;

        private SignatureRequest(Builder builder) {
            this.env = builder.env;
            this.newHeaders = builder.newHeaders;
            this.keyId = builder.keyId;
            this.headersToSign = builder.headersToSign;
            this.privateKey = builder.privateKey;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder implements io.helidon.common.Builder<SignatureRequest> {
            private RSAPrivateKey privateKey;
            private SecurityEnvironment env;
            private Map<String, List<String>> newHeaders;
            private String keyId;
            private SignedHeadersConfig headersConfig;
            private List<String> headersToSign;

            private Builder() {
            }

            @Override
            public SignatureRequest build() {
                this.headersToSign = headersConfig.headers(env.method(), env.headers());
                return new SignatureRequest(this);
            }

            public Builder env(SecurityEnvironment env) {
                this.env = env;
                return this;
            }

            public Builder newHeaders(Map<String,
                    List<String>> newHeaders) {
                this.newHeaders = newHeaders;
                return this;
            }

            public Builder keyId(String keyId) {
                this.keyId = keyId;
                return this;
            }

            public Builder headersConfig(SignedHeadersConfig headersConfig) {
                this.headersConfig = headersConfig;
                return this;
            }

            public Builder privateKey(RSAPrivateKey privateKey) {
                this.privateKey = privateKey;
                return this;
            }
        }
    }
}
