/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpsign;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.pki.KeyConfig;
import io.helidon.security.SecurityEnvironment;

/**
 * Class wrapping signature and fields needed to build and validate it.
 */
class HttpSignature {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger(HttpSignature.class.getName());
    private static final List<String> DEFAULT_HEADERS = List.of("date");
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final String keyId;
    private final String algorithm;
    private final List<String> headers;
    // Backward compatibility with Helidon versions until 3.0.0
    // the signed string contained a trailing new line
    private final boolean backwardCompatibleEol;
    private String base64Signature;

    private byte[] signatureBytes;

    HttpSignature(String keyId, String algorithm, List<String> headers, boolean backwardCompatibleEol) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.headers = headers;
        this.backwardCompatibleEol = backwardCompatibleEol;
    }

    private HttpSignature(String keyId,
                          String algorithm,
                          List<String> headers,
                          String base64Signature,
                          boolean backwardCompatibleEol) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.headers = headers;
        this.base64Signature = base64Signature;
        this.backwardCompatibleEol = backwardCompatibleEol;
    }

    static HttpSignature fromHeader(String header, boolean backwardCompatibleEol) {
        /*keyId="rsa-key-1",algorithm="rsa-sha256",
                headers="(request-target) host date digest content-length",
                signature="Base64(RSA-SHA256(signing string))"*/

        // required
        String keyId = null;
        // required
        String algorithm = null;
        List<String> headers = DEFAULT_HEADERS;
        // required
        String signature = null;

        // according to spec, I must go from beginning and latest one wins
        int b = 0;
        while (true) {
            int c = header.indexOf(',', b);
            int eq = header.indexOf('=', b);
            if (eq == -1) {
                return new HttpSignature(keyId, algorithm, headers, signature, backwardCompatibleEol);
            }
            if (eq > c) {
                b = c + 1;
            }
            int qb = header.indexOf('"', eq);
            if (qb == -1) {
                return new HttpSignature(keyId, algorithm, headers, signature, backwardCompatibleEol);
            }
            int qe = header.indexOf('"', qb + 1);
            if (qe == -1) {
                return new HttpSignature(keyId, algorithm, headers, signature, backwardCompatibleEol);
            }

            String name = header.substring(b, eq).trim();
            String unquotedValue = header.substring(qb + 1, qe);
            switch (name) {
            case "keyId":
                keyId = unquotedValue;
                break;
            case "algorithm":
                algorithm = unquotedValue;
                break;
            case "signature":
                signature = unquotedValue;
                break;
            case "headers":
                headers = Arrays.asList(unquotedValue.split(" "));
                break;
            default:
                LOGGER.finest(() -> "Invalid signature header field: " + name + ": \"" + unquotedValue + "\"");
                break;

            }
            b = qe + 1;
            if (b >= header.length()) {
                return new HttpSignature(keyId, algorithm, headers, signature, backwardCompatibleEol);
            }
        }
    }

    static HttpSignature sign(SecurityEnvironment env,
                              OutboundTargetDefinition outboundDefinition,
                              Map<String, List<String>> newHeaders,
                              boolean backwardCompatibleEol) {

        HttpSignature signature = new HttpSignature(outboundDefinition.keyId(),
                                                    outboundDefinition.algorithm(),
                                                    outboundDefinition.signedHeadersConfig()
                                                            .headers(env.method(), env.headers()),
                                                    backwardCompatibleEol);

        // validate algorithm is OK
        //let's try to validate the signature
        switch (signature.getAlgorithm()) {
        case HttpSignProvider.ALGORITHM_RSA:
            signature.signatureBytes = signature.signRsaSha256(env,
                                                               outboundDefinition.keyConfig()
                                                                       .orElseThrow(() -> new HttpSignatureException(
                                                                               "Private key configuration must be present to use "
                                                                                       + HttpSignProvider.ALGORITHM_RSA + " "
                                                                                       + "algorithm")),
                                                               newHeaders);
            break;
        case HttpSignProvider.ALGORITHM_HMAC:
            signature.signatureBytes = signature.signHmacSha256(env,
                                                                outboundDefinition.hmacSharedSecret()
                                                                        .orElseThrow(() -> new HttpSignatureException(
                                                                                "HMAC shared secret must be configured to use "
                                                                                        + HttpSignProvider.ALGORITHM_HMAC
                                                                                        + " algorithm")),
                                                                newHeaders);
            break;
        default:
            throw new HttpSignatureException("Unsupported signature algorithm: " + signature.getAlgorithm());
        }

        signature.base64Signature = Base64.getEncoder().encodeToString(signature.signatureBytes);
        return signature;
    }

    String toSignatureHeader() {
        return "keyId=\"" + keyId + "\","
                + "algorithm=\"" + algorithm + "\","
                + "headers=\"" + String.join(" ", headers) + "\","
                + "signature=\"" + base64Signature + "\"";
    }

    String getKeyId() {
        return keyId;
    }

    String getAlgorithm() {
        return algorithm;
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
        if (null == algorithm) {
            problems.add("algorithm is a mandatory signature header component");
        } else {
            switch (algorithm) {
            case HttpSignProvider.ALGORITHM_RSA:
            case HttpSignProvider.ALGORITHM_HMAC:
                break;
            default:
                problems.add("Unsupported signature algorithm: " + algorithm);
                break;
            }
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

    Optional<String> validate(SecurityEnvironment env,
                              InboundClientDefinition clientDefinition,
                              List<String> requiredHeaders) {

        // validate algorithm is OK
        if (!algorithm.equalsIgnoreCase(clientDefinition.algorithm())) {
            return Optional.of("Algorithm of signature is " + algorithm + ", configured: " + clientDefinition.algorithm());
        }

        for (String requiredHeader : requiredHeaders) {
            if (!this.headers.contains(requiredHeader)) {
                return Optional.of("Header " + requiredHeader + " is required, yet not signed");
            }
        }

        //let's try to validate the signature
        switch (algorithm) {
        case HttpSignProvider.ALGORITHM_RSA:
            return validateRsaSha256(env, clientDefinition);
        case HttpSignProvider.ALGORITHM_HMAC:
            return validateHmacSha256(env, clientDefinition);
        default:
            return Optional.of("Unsupported algorithm: " + algorithm);
        }
    }

    private byte[] signRsaSha256(SecurityEnvironment env, KeyConfig keyConfig, Map<String, List<String>> newHeaders) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyConfig.privateKey().orElseThrow(() ->
                                                                          new HttpSignatureException(
                                                                                  "Private key is required, yet not "
                                                                                          + "configured")));
            signature.update(getBytesToSign(env, newHeaders));
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new HttpSignatureException(e);
        }
    }

    private Optional<String> validateRsaSha256(SecurityEnvironment env,
                                               InboundClientDefinition clientDefinition) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(clientDefinition.keyConfig()
                                         .orElseThrow(() -> new HttpSignatureException("RSA public key configuration is "
                                                                                               + "required"))
                                         .publicKey()
                                         .orElseThrow(() -> new HttpSignatureException(
                                                 "Public key is required, yet not configured")));
            signature.update(getBytesToSign(env, null));

            if (!signature.verify(this.signatureBytes)) {
                return Optional.of("Signature is not valid");
            }

            return Optional.empty();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.FINEST, "SHA256withRSA algorithm not found", e);
            return Optional.of("SHA256withRSA algorithm not found: " + e.getMessage());
        } catch (InvalidKeyException e) {
            LOGGER.log(Level.FINEST, "Invalid RSA key", e);
            return Optional.of("Invalid RSA key: " + e.getMessage());
        } catch (SignatureException e) {
            LOGGER.log(Level.FINEST, "Signature exception", e);
            return Optional.of("SignatureException: " + e.getMessage());
        }
    }

    private byte[] signHmacSha256(SecurityEnvironment env, byte[] secret, Map<String, List<String>> newHeaders) {
        try {
            String algorithm = "HmacSHA256";
            Mac mac = Mac.getInstance(algorithm);

            SecretKeySpec secretKey = new SecretKeySpec(secret, algorithm);
            mac.init(secretKey);

            return mac.doFinal(getBytesToSign(env, newHeaders));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new HttpSignatureException(e);
        }
    }

    private Optional<String> validateHmacSha256(SecurityEnvironment env,
                                                InboundClientDefinition clientDefinition) {
        try {
            byte[] signature = signHmacSha256(env, clientDefinition.hmacSharedSecret().orElse(EMPTY_BYTES), null);
            if (!Arrays.equals(signature, this.signatureBytes)) {
                return Optional.of("Signature is not valid");
            }
            return Optional.empty();
        } catch (SecurityException e) {
            LOGGER.log(Level.FINEST, "Failed to validate hmac-sha256", e);
            return Optional.of("Failed to validate hmac-sha256: " + e.getMessage());
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

        if (backwardCompatibleEol) {
            toSign = toSign + "\n";
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Data to sign: " + toSign);
        }

        return toSign;
    }
}
