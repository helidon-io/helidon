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

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import io.helidon.common.OptionalHelper;
import io.helidon.security.Principal;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtUtil;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.util.AbacSupport;

import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Implementation of {@link JsonWebToken} with additional support of {@link AbacSupport}.
 */
public class JsonWebTokenImpl implements JsonWebToken, Principal {
    private final Jwt jwt;
    private final SignedJwt signed;
    private final String id;

    private final AbacSupport properties;
    private final String name;

    public JsonWebTokenImpl(Jwt jwt, SignedJwt signed) {
        this.jwt = jwt;
        this.signed = signed;
        BasicAttributes container = new BasicAttributes();
        jwt.getPayloadClaims()
                .forEach((key, jsonValue) -> container.put(key, JwtUtil.toObject(jsonValue)));

        jwt.getEmail().ifPresent(value -> container.put("email", value));
        jwt.getEmailVerified().ifPresent(value -> container.put("email_verified", value));
        jwt.getLocale().ifPresent(value -> container.put("locale", value));
        jwt.getFamilyName().ifPresent(value -> container.put("family_name", value));
        jwt.getGivenName().ifPresent(value -> container.put("given_name", value));
        jwt.getFullName().ifPresent(value -> container.put("full_name", value));

        this.properties = container;

        String subject = jwt.getSubject()
                .orElseThrow(() -> new JwtException("JWT does not contain subject claim, cannot create principal."));

        this.name = OptionalHelper.from(jwt.getUserPrincipal())
                .or(jwt::getPreferredUsername).asOptional()
                .orElse(subject);

        this.id = subject;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getClaimNames() {
        return jwt.getPayloadClaims().keySet();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getClaim(String claimName) {
        try {
            return (T) getClaim(Claims.valueOf(claimName));
        } catch (IllegalArgumentException e) {
            //If claimName is name of the custom claim
            return (T) getJsonValue(claimName).orElse(null);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getClaim(String claimName, Class<T> clazz) {
        try {
            Claims claims = Claims.valueOf(claimName);
            return JsonValue.class.isAssignableFrom(clazz) ?
                    (T) getJsonValue(claimName).orElse(null) :
                    (T) getClaim(claims);
        } catch (IllegalArgumentException e) {
            //If claimName is name of the custom claim
            Object value = getJsonValue(claimName).orElse(null);
            Class verify = clazz;
            if ((value != null)
                    && (verify != ClaimValue.class)
                    && (verify != Optional.class)
                    && !verify.isAssignableFrom(value.getClass())) {
                throw new SecurityException("Cannot set instance of " + value.getClass().getName() + " to the field of type " + verify.getName());
            }
            return (T) value;
        }
    }

    private Object getClaim(Claims claims) {
        switch (claims) {
        case raw_token:
            return signed.getTokenContent();
        case groups:
            return jwt.getUserGroups().map(HashSet::new).orElse(null);
        case aud:
            return jwt.getAudience().map(HashSet::new).orElse(null);
        case email_verified:
            return jwt.getEmailVerified().orElse(null);
        case phone_number_verified:
            return jwt.getPhoneNumberVerified().orElse(null);
        default:
            //do nothing, just continue to processing based on type
        }

        String claimName = claims.name();
        Optional<JsonValue> json = getJsonValue(claimName);

        return json.map(value -> convert(claims, value)).orElse(null);
    }

    private Optional<JsonValue> getJsonValue(String claimName) {
        if (Claims.raw_token.name().equals(claimName)) {
            // special case, raw token is not really a claim
            return Optional.of(Json.createValue(signed.getTokenContent()));
        }
        return OptionalHelper
                .from(jwt.getPayloadClaim(claimName))
                .or(() -> jwt.getHeaderClaim(claimName))
                .asOptional();
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

    @Override
    public Object getAttributeRaw(String key) {
        return properties.getAttributeRaw(key);
    }

    @Override
    public Collection<String> getAttributeNames() {
        return properties.getAttributeNames();
    }

    @Override
    public String getId() {
        return id;
    }
}
