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
package io.helidon.microprofile.jwt.auth;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import io.helidon.common.OptionalHelper;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * TODO javadoc.
 */
public class JsonWebTokenImpl implements JsonWebToken {
    private final Jwt jwt;
    private final SignedJwt signed;

    JsonWebTokenImpl(Jwt jwt, SignedJwt signed) {
        this.jwt = jwt;
        this.signed = signed;
    }

    @Override
    public String getName() {
        return jwt.getUserPrincipal().get();
    }

    @Override
    public Set<String> getClaimNames() {
        return jwt.getPayloadClaims().keySet();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getClaim(String claimName) {
        Claims claims = Claims.valueOf(claimName);
        return (T) getClaim(claims);
    }

    private Object getClaim(Claims claims) {
        switch (claims) {
        case raw_token:
            return signed.getTokenContent();
        case groups:
            return jwt.getUserGroups().map(HashSet::new).orElseGet(HashSet::new);
        case aud:
            return jwt.getAudience().map(HashSet::new).orElseGet(HashSet::new);
        case email_verified:
            return jwt.getEmailVerified().orElseGet(null);
        case phone_number_verified:
            return jwt.getPhoneNumberVerified().orElseGet(null);
        default:
            //do nothing, just continue to processing based on type
        }

        String claimName = claims.name();
        JsonObject payloadJson = jwt.getPayloadJson();
        JsonObject headerJson = jwt.getHeaderJson();

        Optional<JsonValue> json = OptionalHelper.from(Optional.ofNullable(payloadJson.get(claimName)))
                .or(() -> Optional.ofNullable(headerJson.get(claimName)))
                .asOptional();

        return json.map(value -> convert(claims, value)).orElse(null);
    }

    private Object convert(Claims claims, JsonValue value) {
        Class<?> claimClass = claims.getType();
        if (claimClass.equals(String.class)) {
            if (value instanceof JsonString) {
                return ((JsonString) value).getString();
            }
        }
        if (claimClass.equals(Long.class)) {
            if (value instanceof JsonNumber) {
                return ((JsonNumber) value).longValue();
            } else if (value instanceof JsonString) {
                return Long.parseLong(((JsonString) value).getString());
            }
            return Long.parseLong(value.toString());
        }
        if (claimClass.equals(JsonObject.class)) {
            return value;
        }

        if (value instanceof JsonString) {
            return ((JsonString) value).getString();
        }

        return value;
    }
}
