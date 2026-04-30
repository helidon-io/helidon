/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.pki.Keys;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.DateTime;
import io.helidon.security.SecurityEnvironment;

/**
 * Class wrapping signature and fields needed to build and validate it.
 */
class HttpSignature {
    private static final DateTimeFormatter DATE_FORMATTER = DateTime.RFC_1123_DATE_TIME;
    private static final System.Logger LOGGER = System.getLogger(HttpSignature.class.getName());
    private static final List<String> DEFAULT_HEADERS = List.of("date");
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final ZoneId GMT = ZoneId.of("GMT");

    private final String keyId;
    private final String algorithm;
    private final List<String> headers;
    // Backward compatibility with Helidon versions until 3.0.0
    // the signed string contained a trailing new line
    private final boolean backwardCompatibleEol;
    private final String parseError;
    private String base64Signature;

    private byte[] signatureBytes;

    HttpSignature(String keyId, String algorithm, List<String> headers, boolean backwardCompatibleEol) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.headers = headers;
        this.backwardCompatibleEol = backwardCompatibleEol;
        this.parseError = null;
    }

    private HttpSignature(String keyId,
                          String algorithm,
                          List<String> headers,
                          String base64Signature,
                          boolean backwardCompatibleEol,
                          String parseError) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.headers = headers;
        this.base64Signature = base64Signature;
        this.backwardCompatibleEol = backwardCompatibleEol;
        this.parseError = parseError;
    }

    static HttpSignature fromHeader(String header, boolean backwardCompatibleEol) {
        return fromHeader(header, backwardCompatibleEol, false);
    }

    static HttpSignature fromAuthorizationHeader(String header, boolean backwardCompatibleEol) {
        return fromHeader(header, backwardCompatibleEol, true);
    }

    private static HttpSignature fromHeader(String header, boolean backwardCompatibleEol, boolean strict) {
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
            int eq = header.indexOf('=', b);
            if (eq == -1) {
                return new HttpSignature(keyId,
                                         algorithm,
                                         headers,
                                         signature,
                                         backwardCompatibleEol,
                                         strict && b < header.length()
                                                 ? "Unexpected data after signature parameters"
                                                 : null);
            }
            int c = header.indexOf(',', b);
            if (c != -1 && c < eq) {
                if (strict && !header.substring(b, c).trim().isEmpty()) {
                    return new HttpSignature(keyId,
                                             algorithm,
                                             headers,
                                             signature,
                                             backwardCompatibleEol,
                                             "Unexpected data after signature parameters");
                }
                b = c + 1;
                continue;
            }
            int qb = header.indexOf('"', eq);
            if (qb == -1) {
                return new HttpSignature(keyId,
                                         algorithm,
                                         headers,
                                         signature,
                                         backwardCompatibleEol,
                                         strict ? "Unexpected data after signature parameters" : null);
            }
            int qe = header.indexOf('"', qb + 1);
            if (qe == -1) {
                return new HttpSignature(keyId,
                                         algorithm,
                                         headers,
                                         signature,
                                         backwardCompatibleEol,
                                         strict ? "Unexpected data after signature parameters" : null);
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
                if (strict) {
                    return new HttpSignature(keyId,
                                             algorithm,
                                             headers,
                                             signature,
                                             backwardCompatibleEol,
                                             "Unexpected data after signature parameters");
                }
                LOGGER.log(Level.TRACE, () -> "Invalid signature header field: " + name + ": \"" + unquotedValue + "\"");
                break;

            }
            b = qe + 1;
            while (b < header.length() && Character.isWhitespace(header.charAt(b))) {
                b++;
            }
            if (b >= header.length()) {
                return new HttpSignature(keyId, algorithm, headers, signature, backwardCompatibleEol, null);
            }
            if (header.charAt(b) != ',') {
                return new HttpSignature(keyId,
                                         algorithm,
                                         headers,
                                         signature,
                                         backwardCompatibleEol,
                                         strict ? "Unexpected data after signature parameters" : null);
            }
            b++;
            while (b < header.length() && Character.isWhitespace(header.charAt(b))) {
                b++;
            }
        }
    }

    static HttpSignature sign(SecurityEnvironment env,
                              OutboundTargetDefinition outboundDefinition,
                              Map<String, List<String>> newHeaders,
                              boolean backwardCompatibleEol) {
        List<String> signedHeaders = outboundDefinition.signedHeadersConfig()
                .headers(env.method(), env.headers());
        if (outboundDefinition.header() == HttpSignHeader.AUTHORIZATION) {
            signedHeaders = signedHeaders.stream()
                    .filter(header -> !"authorization".equalsIgnoreCase(header))
                    .toList();
        }

        HttpSignature signature = new HttpSignature(outboundDefinition.keyId(),
                                                    outboundDefinition.algorithm(),
                                                    signedHeaders,
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

        if (parseError != null) {
            problems.add(parseError);
        }
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
            LOGGER.log(Level.TRACE, "Cannot get bytes from base64: " + base64Signature, e);
            problems.add("cannot get bytes from base64 encoded signature: " + e.getMessage());
        }

        if (problems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("HttpSignature is not valid. Problems: " + String.join(", ", problems));
    }

    Optional<String> validate(SecurityEnvironment env,
                              InboundClientDefinition clientDefinition,
                              List<String> requiredHeaders,
                              Duration dateValidity) {

        // validate algorithm is OK
        if (!algorithm.equalsIgnoreCase(clientDefinition.algorithm())) {
            return Optional.of("Algorithm of signature is " + algorithm + ", configured: " + clientDefinition.algorithm());
        }

        for (String requiredHeader : requiredHeaders) {
            if (this.headers.stream().noneMatch(requiredHeader::equalsIgnoreCase)) {
                return Optional.of("Header " + requiredHeader + " is required, yet not signed");
            }
        }

        if (!dateValidity.isZero() && headers.stream().anyMatch("date"::equalsIgnoreCase)) {
            List<String> dateHeaderValues = env.headers().get("date");
            if (dateHeaderValues == null) {
                for (Map.Entry<String, List<String>> header : env.headers().entrySet()) {
                    if ("date".equalsIgnoreCase(header.getKey())) {
                        dateHeaderValues = header.getValue();
                        break;
                    }
                }
            }

            if (dateHeaderValues == null || dateHeaderValues.isEmpty()) {
                return Optional.of("Date header is signed, but missing from request");
            }
            if (dateHeaderValues.size() != 1) {
                return Optional.of("Date header must contain exactly one value");
            }

            Instant dateHeader;
            ZonedDateTime serverTime = env.time();
            Instant now = serverTime.toInstant();
            try {
                dateHeader = DateTime.parse(dateHeaderValues.get(0), serverTime).toInstant();
            } catch (DateTimeParseException e) {
                return Optional.of("Date header cannot be parsed: " + e.getMessage());
            }

            if (dateHeader.isBefore(now.minus(dateValidity)) || dateHeader.isAfter(now.plus(dateValidity))) {
                return Optional.of("Date header is outside the allowed validity interval");
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

    private byte[] signRsaSha256(SecurityEnvironment env, Keys keyConfig, Map<String, List<String>> newHeaders) {
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
                signature.update(getBytesToValidate(env, null));

            if (!signature.verify(this.signatureBytes)) {
                return Optional.of("Signature is not valid");
            }

            return Optional.empty();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.TRACE, "SHA256withRSA algorithm not found", e);
            return Optional.of("SHA256withRSA algorithm not found: " + e.getMessage());
        } catch (InvalidKeyException e) {
            LOGGER.log(Level.TRACE, "Invalid RSA key", e);
            return Optional.of("Invalid RSA key: " + e.getMessage());
        } catch (SignatureException e) {
            LOGGER.log(Level.TRACE, "Signature exception", e);
            return Optional.of("SignatureException: " + e.getMessage());
        } catch (SecurityException e) {
            LOGGER.log(Level.TRACE, "Failed to validate rsa-sha256", e);
            return Optional.of("Failed to validate rsa-sha256: " + e.getMessage());
        }
    }

    private byte[] signHmacSha256(SecurityEnvironment env,
                                  byte[] secret,
                                  Map<String, List<String>> newHeaders) {
        try {
            String algorithm = "HmacSHA256";
            Mac mac = Mac.getInstance(algorithm);

            SecretKeySpec secretKey = new SecretKeySpec(secret, algorithm);
            mac.init(secretKey);

            return mac.doFinal(getBytesToValidate(env, newHeaders));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new HttpSignatureException(e);
        }
    }

    private Optional<String> validateHmacSha256(SecurityEnvironment env,
                                                InboundClientDefinition clientDefinition) {
        try {
            byte[] signature = signHmacSha256(env, clientDefinition.hmacSharedSecret().orElse(EMPTY_BYTES), null);
            if (!MessageDigest.isEqual(signature, this.signatureBytes)) {
                return Optional.of("Signature is not valid");
            }
            return Optional.empty();
        } catch (SecurityException e) {
            LOGGER.log(Level.TRACE, "Failed to validate hmac-sha256", e);
            return Optional.of("Failed to validate hmac-sha256: " + e.getMessage());
        }
    }

    private byte[] getBytesToSign(SecurityEnvironment env, Map<String, List<String>> newHeaders) {
        return getSignedString(newHeaders, env).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getBytesToValidate(SecurityEnvironment env, Map<String, List<String>> newHeaders) {
        return getSignedString(newHeaders, env).getBytes(StandardCharsets.UTF_8);
    }

    String getSignedString(Map<String, List<String>> newHeaders, SecurityEnvironment env) {
        Map<String, List<String>> requestHeaders = env.headers();
        List<String> linesToSign = new LinkedList<>();

        for (String header : this.headers) {
            if ("(request-target)".equalsIgnoreCase(header)) {
                //special case
                String path = env.requestedPath().rawPath();
                if (path.isEmpty()) {
                    path = "/";
                }
                Optional<UriQuery> requestedQuery = env.requestedQuery();
                String target = requestedQuery.isPresent() ? path + "?" + requestedQuery.get().rawValue() : path;
                linesToSign.add(header
                                        + ": " + env.requestedMethod().toLowerCase()
                                        + " " + target);
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
                        String date = ZonedDateTime.now(GMT).format(DATE_FORMATTER);
                        headerValues = List.of(date);
                        newHeaders.put("date", headerValues);

                        LOGGER.log(Level.TRACE, () -> "Added date header to request: " + date);
                    } else if ("host".equalsIgnoreCase(header)) {
                        URI uri = env.targetUri();

                        String host = uri.getHost();
                        if (uri.getPort() != -1) {
                            host = host + ":" + uri.getPort();
                        }
                        String finalHost = host;
                        headerValues = List.of(host);
                        newHeaders.put("host", headerValues);

                        LOGGER.log(Level.TRACE, () -> "Added host header to request: " + finalHost);
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

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Data to sign: " + toSign);
        }

        return toSign;
    }
}
