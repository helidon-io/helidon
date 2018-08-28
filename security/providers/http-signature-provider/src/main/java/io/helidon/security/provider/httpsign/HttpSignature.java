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

package io.helidon.security.provider.httpsign;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.pki.KeyConfig;
import io.helidon.security.SecurityEnvironment;

/**
 * Class wrapping signature and fields needed to build and validate it.
 */
class HttpSignature {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger(HttpSignature.class.getName());
    private static final List<String> DEFAULT_HEADERS = CollectionsHelper.listOf("date");
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final String keyId;
    private final String algorithm;
    private final List<String> headers;
    private String base64Signature;

    private byte[] signatureBytes;

    HttpSignature(String keyId, String algorithm, List<String> headers) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.headers = headers;
    }

    private HttpSignature(String header, String keyId,
                          String algorithm,
                          List<String> headers,
                          String base64Signature) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.headers = headers;
        this.base64Signature = base64Signature;
    }

    static HttpSignature fromHeader(String header) {
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
                return new HttpSignature(header, keyId, algorithm, headers, signature);
            }
            if (eq > c) {
                b = c + 1;
            }
            int qb = header.indexOf('"', eq);
            if (qb == -1) {
                return new HttpSignature(header, keyId, algorithm, headers, signature);
            }
            int qe = header.indexOf('"', qb + 1);
            if (qe == -1) {
                return new HttpSignature(header, keyId, algorithm, headers, signature);
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
                return new HttpSignature(header, keyId, algorithm, headers, signature);
            }
        }
    }

    static HttpSignature sign(SecurityEnvironment env,
                              OutboundTargetDefinition outboundDefinition,
                              Map<String, List<String>> newHeaders) {

        HttpSignature signature = new HttpSignature(outboundDefinition.getKeyId(),
                                                    outboundDefinition.getAlgorithm(),
                                                    outboundDefinition.getSignedHeadersConfig()
                                                            .getHeaders(env.getMethod(), env.getHeaders()));

        // validate algorithm is OK
        //let's try to validate the signature
        switch (signature.getAlgorithm()) {
        case HttpSignProvider.ALGORITHM_RSA:
            signature.signatureBytes = signature.signRsaSha256(env,
                                                               outboundDefinition.getKeyConfig()
                                                                       .orElseThrow(() -> new HttpSignatureException(
                                                                               "Private key configuration must be present to use "
                                                                                       + HttpSignProvider.ALGORITHM_RSA + " "
                                                                                       + "algorithm")),
                                                               newHeaders);
            break;
        case HttpSignProvider.ALGORITHM_HMAC:
            signature.signatureBytes = signature.signHmacSha256(env,
                                                                outboundDefinition.getHmacSharedSecret()
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
        if (!algorithm.equalsIgnoreCase(clientDefinition.getAlgorithm())) {
            return Optional.of("Algorithm of signature is " + algorithm + ", configured: " + clientDefinition.getAlgorithm());
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
            signature.initSign(keyConfig.getPrivateKey().orElseThrow(() ->
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
            signature.initVerify(clientDefinition.getKeyConfig()
                                         .orElseThrow(() -> new HttpSignatureException("RSA public key configuration is "
                                                                                               + "required"))
                                         .getPublicKey()
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
            byte[] signature = signHmacSha256(env, clientDefinition.getHmacSharedSecret().orElse(EMPTY_BYTES), null);
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
        StringBuilder toSign = new StringBuilder();
        Map<String, List<String>> requestHeaders = env.getHeaders();

        for (String header : this.headers) {
            if ("(request-target)".equals(header)) {
                //special case
                toSign.append(header)
                        .append(": ")
                        .append(env.getMethod().toLowerCase())
                        .append(" ")
                        .append(env.getPath().orElse("/"))
                        .append('\n');
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
                        headerValues = CollectionsHelper.listOf(date);
                        newHeaders.put("date", headerValues);

                        LOGGER.finest(() -> "Added date header to request: " + date);
                    } else if ("host".equalsIgnoreCase(header)) {
                        URI uri = env.getTargetUri();

                        String host = uri.getHost() + ":" + uri.getPort();
                        headerValues = CollectionsHelper.listOf(host);
                        newHeaders.put("host", headerValues);

                        LOGGER.finest(() -> "Added host header to request: " + host);
                    } else {
                        throw new HttpSignatureException("Header " + header + " is required for signature, yet not defined in "
                                                                 + "request");
                    }
                }

                toSign.append(header)
                        .append(": ")
                        .append(String.join(" ", headerValues))
                        .append('\n');
            }
        }

        LOGGER.finest(() -> "Data to sign: " + toSign);

        return toSign.toString();
    }
}
