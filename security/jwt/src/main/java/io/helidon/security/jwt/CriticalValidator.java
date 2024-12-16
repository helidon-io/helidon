/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.Errors;

final class CriticalValidator implements ClaimValidator {

    private static final Set<String> INVALID_CRIT_HEADERS;

    static {
        Set<String> names = new HashSet<>();
        names.add(JwtHeaders.ALGORITHM);
        names.add(JwtHeaders.ENCRYPTION);
        names.add(JwtHeaders.TYPE);
        names.add(JwtHeaders.CONTENT_TYPE);
        names.add(JwtHeaders.KEY_ID);
        names.add(JwtHeaders.JWK_SET_URL);
        names.add(JwtHeaders.JSON_WEB_KEY);
        names.add(JwtHeaders.X509_URL);
        names.add(JwtHeaders.X509_CERT_CHAIN);
        names.add(JwtHeaders.X509_CERT_SHA1_THUMB);
        names.add(JwtHeaders.X509_CERT_SHA256_THUMB);
        names.add(JwtHeaders.CRITICAL);
        names.add(JwtHeaders.COMPRESSION_ALGORITHM);
        names.add(JwtHeaders.AGREEMENT_PARTYUINFO);
        names.add(JwtHeaders.AGREEMENT_PARTYVINFO);
        names.add(JwtHeaders.EPHEMERAL_PUBLIC_KEY);

        INVALID_CRIT_HEADERS = Set.copyOf(names);
    }

    @Override
    public JwtScope jwtScope() {
        return JwtScope.HEADER;
    }

    @Override
    public Set<String> claims() {
        return Set.of(Jwt.CRITICAL);
    }

    //   Taken from RFC7515 - 4.1.11 "crit" (Critical) Header Parameter
    //
    //   The "crit" (critical) Header Parameter indicates that extensions to
    //   this specification and/or [JWA] are being used that MUST be
    //   understood and processed.  Its value is an array listing the Header
    //   Parameter names present in the JOSE Header that use those extensions.
    //   If any of the listed extension Header Parameters are not understood
    //   and supported by the recipient, then the JWS is invalid.  Producers
    //   MUST NOT include Header Parameter names defined by this specification
    //   or [JWA] for use with JWS, duplicate names, or names that do not
    //   occur as Header Parameter names within the JOSE Header in the "crit"
    //   list.  Producers MUST NOT use the empty list "[]" as the "crit"
    //   value.  Recipients MAY consider the JWS to be invalid if the critical
    //   list contains any Header Parameter names defined by this
    //   specification or [JWA] for use with JWS or if any other constraints
    //   on its use are violated.  When used, this Header Parameter MUST be
    //   integrity protected; therefore, it MUST occur only within the JWS
    //   Protected Header.  Use of this Header Parameter is OPTIONAL.  This
    //   Header Parameter MUST be understood and processed by implementations.
    //
    //   An example use, along with a hypothetical "exp" (expiration time)
    //   field is:
    //
    //     {"alg":"ES256",
    //      "crit":["exp"],
    //      "exp":1363284000
    //     }
    @Override
    public void validate(Jwt jwt, Errors.Collector collector, List<ClaimValidator> validators) {
        Optional<List<String>> maybeCritical = jwt.headers().critical();
        if (maybeCritical.isPresent()) {
            List<String> critical = maybeCritical.get();
            if (critical.isEmpty()) {
                collector.fatal(jwt, "JWT critical header must not be empty");
                return;
            }
            checkAllCriticalAvailable(jwt, critical, collector);
            if (collector.hasFatal()) {
                return;
            }
            checkDuplicity(jwt, critical, collector);
            if (collector.hasFatal()) {
                return;
            }
            checkInvalidHeaders(jwt, critical, collector);
            if (collector.hasFatal()) {
                return;
            }
            checkNotSupportedHeaders(jwt, critical, collector, validators);
        }
    }

    private void checkAllCriticalAvailable(Jwt jwt, List<String> critical, Errors.Collector collector) {
        Set<String> headerClaims = jwt.headers().headerClaims().keySet();
        boolean containsAllCritical = headerClaims.containsAll(critical);
        if (!containsAllCritical) {
            collector.fatal(jwt, "JWT must contain " + critical + ", yet it contains: " + headerClaims);
        }
    }

    private void checkNotSupportedHeaders(Jwt jwt,
                                          List<String> critical,
                                          Errors.Collector collector,
                                          List<ClaimValidator> validators) {
        Set<String> supportedHeaderClaims = validators
                .stream()
                .filter(claimValidator -> claimValidator.jwtScope() == JwtScope.HEADER)
                .map(ClaimValidator::claims)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        boolean containUnsupported = supportedHeaderClaims.containsAll(critical);
        if (!containUnsupported) {
            collector.fatal(jwt, "JWT is required to process " + critical
                    + ", yet it process only " + supportedHeaderClaims);
        }
    }

    private void checkDuplicity(Jwt jwt, List<String> critical, Errors.Collector collector) {
        Set<String> copy = new HashSet<>(critical);
        if (copy.size() != critical.size()) {
            collector.fatal(jwt, "JWT critical header contains duplicated values: " + critical);
        }
    }

    private void checkInvalidHeaders(Jwt jwt, List<String> critical, Errors.Collector collector) {
        for (String header : critical) {
            if (INVALID_CRIT_HEADERS.contains(header)) {
                collector.fatal(jwt, "Required critical header value '" + header + "' is invalid. "
                        + "This required header is defined among JWA, JWE or JWS headers.");
            }
        }
    }
}
