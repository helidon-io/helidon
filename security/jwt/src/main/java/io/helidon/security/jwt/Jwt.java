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

package io.helidon.security.jwt;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;
import io.helidon.security.jwt.jwk.Jwk;

/**
 * JWT token.
 * <p>
 * Representation of a JSON web token (a generic one).
 */
@SuppressWarnings("WeakerAccess") // getters should be public
public class Jwt {

    static final String CRITICAL = "crit";

    static final String ISSUER = "iss";
    static final String SUBJECT = "sub";
    static final String AUDIENCE = "aud";
    static final String EXPIRATION = "exp";
    static final String NOT_BEFORE = "nbf";
    static final String ISSUED_AT = "iat";
    static final String USER_PRINCIPAL = "upn";
    static final String USER_GROUPS = "groups";
    static final String JWT_ID = "jti";
    static final String EMAIL = "email";
    static final String EMAIL_VERIFIED = "email_verified";
    static final String FULL_NAME = "name";
    static final String GIVEN_NAME = "given_name";
    static final String MIDDLE_NAME = "middle_name";
    static final String FAMILY_NAME = "family_name";
    static final String LOCALE = "locale";
    static final String NICKNAME = "nickname";
    static final String PREFERRED_USERNAME = "preferred_username";
    static final String PROFILE = "profile";
    static final String PICTURE = "picture";
    static final String WEBSITE = "website";
    static final String GENDER = "gender";
    static final String BIRTHDAY = "birthday";
    static final String ZONE_INFO = "zoneinfo";
    static final String PHONE_NUMBER = "phone_number";
    static final String PHONE_NUMBER_VERIFIED = "phone_number_verified";
    static final String UPDATED_AT = "updated_at";
    static final String ADDRESS = "address";
    static final String AT_HASH = "at_hash";
    static final String C_HASH = "c_hash";
    static final String NONCE = "nonce";
    static final String SCOPE = "scope";

    /*
     All header information.
     */
    private final JwtHeaders headers;

    /*
    Payload claims
     */
    private final Map<String, JsonValue> payloadClaims;

    // iss
    // "iss":"accounts.google.com",
    private final Optional<String> issuer;
    // exp
    // "exp":1495734457,
    /*
     The "exp" (expiration time) claim identifies the expiration time on
     or after which the JWT MUST NOT be accepted for processing.  The
     processing of the "exp" claim requires that the current date/time
     MUST be before the expiration date/time listed in the "exp" claim.
     Implementers MAY provide for some small leeway, usually no more than
     a few minutes, to account for clock skew.  Its value MUST be a number
     containing a NumericDate value.  Use of this claim is OPTIONAL.
     */
    private final Optional<Instant> expirationTime;
    /*
     The "iat" (issued at) claim identifies the time at which the JWT was
   issued.  This claim can be used to determine the age of the JWT.  Its
   value MUST be a number containing a NumericDate value.  Use of this
   claim is OPTIONAL.
     */
    // "iat":1495730857,
    private final Optional<Instant> issueTime;

    /*
    The "nbf" (not before) claim identifies the time before which the JWT
   MUST NOT be accepted for processing.  The processing of the "nbf"
   claim requires that the current date/time MUST be after or equal to
   the not-before date/time listed in the "nbf" claim.  Implementers MAY
   provide for some small leeway, usually no more than a few minutes, to
   account for clock skew.  Its value MUST be a number containing a
   NumericDate value.  Use of this claim is OPTIONAL.
    */
    // "nbf":
    private final Optional<Instant> notBefore;
    /*
    The "sub" (subject) claim identifies the principal that is the
   subject of the JWT.  The claims in a JWT are normally statements
   about the subject.  The subject value MUST either be scoped to be
   locally unique in the context of the issuer or be globally unique.
   The processing of this claim is generally application specific.  The
   "sub" value is a case-sensitive string containing a StringOrURI
   value.  Use of this claim is OPTIONAL.
    */
    // "sub":"106482221621567111461",
    private final Optional<String> subject;

    /*
     Microprofile specification JWT Auth:
     A human readable claim that uniquely identifies the subject or user principal of the token, across the MicroProfile
     services the token will be accessed with.
     */
    // "upn":"john.doe@example.org"
    private final Optional<String> userPrincipal;
    /*
    Microprofile specification JWT Auth:
    The token subject’s group memberships that will be mapped to Java EE style application level roles in the MicroProfile
    service container.
     */
    // "groups": ["normalUsers", "abnormalUsers"]
    private final Optional<List<String>> userGroups;
    // "aud":"1048216952820-6a6ke9vrbjlhngbc0al0dkj9qs9tqbk2.apps.googleusercontent.com",
    /*
   The "aud" (audience) claim identifies the recipients that the JWT is
   intended for.  Each principal intended to process the JWT MUST
   identify itself with a value in the audience claim.  If the principal
   processing the claim does not identify itself with a value in the
   "aud" claim when this claim is present, then the JWT MUST be
   rejected.  In the general case, the "aud" value is an array of case-
   sensitive strings, each containing a StringOrURI value.  In the
   special case when the JWT has one audience, the "aud" value MAY be a
   single case-sensitive string containing a StringOrURI value.  The
   interpretation of audience values is generally application specific.
   Use of this claim is OPTIONAL.
    */
    private final Optional<List<String>> audience;

    /*
    The "jti" (JWT ID) claim provides a unique identifier for the JWT.
   The identifier value MUST be assigned in a manner that ensures that
   there is a negligible probability that the same value will be
   accidentally assigned to a different data object; if the application
   uses multiple issuers, collisions MUST be prevented among values
   produced by different issuers as well.  The "jti" claim can be used
   to prevent the JWT from being replayed.  The "jti" value is a case-
   sensitive string.  Use of this claim is OPTIONAL.
    */
    // "jti":"JWT ID"
    private final Optional<String> jwtId;
    // "email":"tomas.langer@gmail.com",
    private final Optional<String> email;
    // "email_verified":true,
    private final Optional<Boolean> emailVerified;
    // "name":"Tomas Langer",
    private final Optional<String> fullName;
    // "given_name":"Tomas",
    private final Optional<String> givenName;
    // "middle_name":""
    private final Optional<String> middleName;
    // "family_name":"Langer",
    private final Optional<String> familyName;
    // "locale":"en-GB"
    private final Optional<Locale> locale;
    // "nickname":""
    private final Optional<String> nickname;
    // "preferred_username": ""
    private final Optional<String> preferredUsername;
    private final Optional<URI> profile;
    // "picture":"https://lh6.googleusercontent.com/-3GYr_xIFNCU/AAAAAAAAAAI/AAAAAAAAAAA/B39Zgxdo8Kc/s96-c/photo.jpg",
    private final Optional<URI> picture;
    private final Optional<URI> website;
    private final Optional<String> gender; //female/male
    private final Optional<LocalDate> birthday;
    // zoneinfo "Europe/Paris"
    private final Optional<ZoneId> timeZone;
    // phone_number: ""
    private final Optional<String> phoneNumber;
    // phone_number_verified: true
    private final Optional<Boolean> phoneNumberVerified;
    // updated_at: 455874455
    private final Optional<Instant> updatedAt;
    // address: json structure
    private final Optional<JwtUtil.Address> address;
    // scope: space separated scopes
    private final Optional<List<String>> scopes;
    /*
    Access Token hash value. Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII
    representation of the access_token value, where the hash algorithm used is the hash algorithm used in the alg Header
    Parameter of the ID Token's JOSE Header. For instance, if the alg is RS256, hash the access_token value with SHA-256, then
    take the left-most 128 bits and base64url encode them. The at_hash value is a case sensitive string.
    If the ID Token is issued from the Authorization Endpoint with an access_token value, which is the case for the response_type
    value code id_token token, this is REQUIRED; otherwise, its inclusion is OPTIONAL.
     */
    // "at_hash":"MpgDTpOEkRLKaB6bAz5IwA"
    private final Optional<byte[]> atHash;
    /*
    Code hash value. Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII
    representation of the code value, where the hash algorithm used is the hash algorithm used in the alg Header Parameter of
    the ID Token's JOSE Header. For instance, if the alg is HS512, hash the code value with SHA-512, then take the left-most
    256 bits and base64url encode them. The c_hash value is a case sensitive string.
    If the ID Token is issued from the Authorization Endpoint with a code, which is the case for the response_type values code
    id_token and code id_token token, this is REQUIRED; otherwise, its inclusion is OPTIONAL.
    */
    // "c_hash"
    private final Optional<byte[]> cHash;
    //Use of the nonce Claim is REQUIRED for hybrid flow.
    private final Optional<String> nonce;

    /**
     * Create a token based on json.
     *
     * @param headers headers
     * @param payloadJson payload
     */
    Jwt(JwtHeaders headers, JsonObject payloadJson) {
        // generic stuff
        this.headers = headers;
        this.payloadClaims = getClaims(payloadJson);

        // known payload
        this.issuer = JwtUtil.getString(payloadJson, ISSUER);
        this.expirationTime = JwtUtil.toInstant(payloadJson, EXPIRATION);
        this.issueTime = JwtUtil.toInstant(payloadJson, ISSUED_AT);
        this.notBefore = JwtUtil.toInstant(payloadJson, NOT_BEFORE);
        this.subject = JwtUtil.getString(payloadJson, SUBJECT);
        JsonValue groups = payloadJson.value(USER_GROUPS, JsonNull.instance());
        if (groups instanceof JsonArray) {
            this.userGroups = JwtUtil.getStrings(payloadJson, USER_GROUPS);
        } else {
            this.userGroups = JwtUtil.getString(payloadJson, USER_GROUPS).map(List::of);
        }

        JsonValue aud = payloadJson.value(AUDIENCE, JsonNull.instance());
        // support both a single string and an array
        if (aud instanceof JsonArray) {
            this.audience = JwtUtil.getStrings(payloadJson, AUDIENCE);
        } else {
            this.audience = JwtUtil.getString(payloadJson, AUDIENCE).map(List::of);
        }

        this.jwtId = JwtUtil.getString(payloadJson, JWT_ID);
        this.email = JwtUtil.getString(payloadJson, EMAIL);
        this.emailVerified = JwtUtil.toBoolean(payloadJson, EMAIL_VERIFIED);
        this.fullName = JwtUtil.getString(payloadJson, FULL_NAME);
        this.givenName = JwtUtil.getString(payloadJson, GIVEN_NAME);
        this.middleName = JwtUtil.getString(payloadJson, MIDDLE_NAME);
        this.familyName = JwtUtil.getString(payloadJson, FAMILY_NAME);
        this.locale = JwtUtil.toLocale(payloadJson, LOCALE);
        this.nickname = JwtUtil.getString(payloadJson, NICKNAME);
        this.preferredUsername = JwtUtil.getString(payloadJson, PREFERRED_USERNAME);
        this.profile = JwtUtil.toUri(payloadJson, PROFILE);
        this.picture = JwtUtil.toUri(payloadJson, PICTURE);
        this.website = JwtUtil.toUri(payloadJson, WEBSITE);
        this.gender = JwtUtil.getString(payloadJson, GENDER);
        this.birthday = JwtUtil.toDate(payloadJson, BIRTHDAY);
        this.timeZone = JwtUtil.toTimeZone(payloadJson, ZONE_INFO);
        this.phoneNumber = JwtUtil.getString(payloadJson, PHONE_NUMBER);
        this.phoneNumberVerified = JwtUtil.toBoolean(payloadJson, PHONE_NUMBER_VERIFIED);
        this.updatedAt = JwtUtil.toInstant(payloadJson, UPDATED_AT);
        this.address = JwtUtil.toAddress(payloadJson, ADDRESS);
        this.atHash = JwtUtil.getByteArray(payloadJson, AT_HASH, "at_hash value");
        this.cHash = JwtUtil.getByteArray(payloadJson, C_HASH, "c_hash value");
        this.nonce = JwtUtil.getString(payloadJson, NONCE);
        this.scopes = JwtUtil.toScopes(payloadJson);
        this.userPrincipal = JwtUtil.getString(payloadJson, USER_PRINCIPAL)
                .or(() -> preferredUsername)
                .or(() -> subject);
    }

    private Jwt(Builder builder) {
        // generic stuff
        this.payloadClaims = new HashMap<>();
        this.payloadClaims.putAll(JwtUtil.transformToJsonValue(builder.payloadClaims));

        // headers
        this.headers = builder.headerBuilder.build();

        // known payload
        this.issuer = builder.issuer;
        this.expirationTime = builder.expirationTime;
        this.issueTime = builder.issueTime;
        this.notBefore = builder.notBefore;
        this.subject = builder.subject.or(() -> toOptionalString(builder.payloadClaims, SUBJECT));
        this.audience = Optional.ofNullable(builder.audience);
        this.jwtId = builder.jwtId;
        this.email = builder.email.or(() -> toOptionalString(builder.payloadClaims, EMAIL));
        this.emailVerified = builder.emailVerified.or(() -> getClaim(builder.payloadClaims, EMAIL_VERIFIED));
        this.fullName = builder.fullName.or(() -> toOptionalString(builder.payloadClaims, FULL_NAME));
        this.givenName = builder.givenName.or(() -> toOptionalString(builder.payloadClaims, GIVEN_NAME));
        this.middleName = builder.middleName.or(() -> toOptionalString(builder.payloadClaims, MIDDLE_NAME));
        this.familyName = builder.familyName.or(() -> toOptionalString(builder.payloadClaims, FAMILY_NAME));
        this.locale = builder.locale.or(() -> getClaim(builder.payloadClaims, LOCALE));
        this.nickname = builder.nickname.or(() -> toOptionalString(builder.payloadClaims, NICKNAME));
        this.preferredUsername = builder.preferredUsername
                .or(() -> toOptionalString(builder.payloadClaims, PREFERRED_USERNAME));
        this.profile = builder.profile.or(() -> getClaim(builder.payloadClaims, PROFILE));
        this.picture = builder.picture.or(() -> getClaim(builder.payloadClaims, PICTURE));
        this.website = builder.website.or(() -> getClaim(builder.payloadClaims, WEBSITE));
        this.gender = builder.gender.or(() -> toOptionalString(builder.payloadClaims, GENDER));
        this.birthday = builder.birthday.or(() -> getClaim(builder.payloadClaims, BIRTHDAY));
        this.timeZone = builder.timeZone.or(() -> getClaim(builder.payloadClaims, ZONE_INFO));
        this.phoneNumber = builder.phoneNumber
                .or(() -> toOptionalString(builder.payloadClaims, PHONE_NUMBER));
        this.phoneNumberVerified = builder.phoneNumberVerified
                .or(() -> getClaim(builder.payloadClaims, PHONE_NUMBER_VERIFIED));

        this.updatedAt = builder.updatedAt;
        this.address = builder.address;
        this.atHash = builder.atHash;
        this.cHash = builder.cHash;
        this.nonce = builder.nonce;
        this.scopes = builder.scopes;

        this.userPrincipal = builder.userPrincipal
                .or(() -> toOptionalString(builder.payloadClaims, USER_PRINCIPAL))
                .or(() -> preferredUsername)
                .or(() -> subject);

        this.userGroups = builder.userGroups;
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> getClaim(Map<String, Object> claims, String claim) {
        return Optional.ofNullable((T) claims.get(claim));
    }

    private static Optional<String> toOptionalString(Map<String, Object> claims, String claim) {
        Object value = claims.get(claim);
        if (null == value) {
            return Optional.empty();
        }
        if (value instanceof String) {
            return Optional.of((String) value);
        }
        return Optional.of(String.valueOf(value));
    }

    /**
     * Get a builder to create a JWT.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private Map<String, JsonValue> getClaims(JsonObject headerJson) {
        Map<String, JsonValue> claims = new HashMap<>();
        headerJson.keysAsStrings().forEach(key -> claims.put(key,
                                                             headerJson.value(key)
                                                                     .orElseThrow(() -> new JwtException(
                                                                             "Claim \"" + key + "\" is missing"))));
        return Collections.unmodifiableMap(claims);
    }

    /**
     * Scopes of this token.
     *
     * @return list of scopes or empty if claim is not defined
     */
    public Optional<List<String>> scopes() {
        return scopes.map(Collections::unmodifiableList);
    }

    /**
     * Get a claim by its name from header.
     *
     * @param claim name of a claim
     * @return claim value if present
     */
    public Optional<JsonValue> headerClaimValue(String claim) {
        return headers.headerClaimValue(claim);
    }

    /**
     * Get a claim by its name from payload.
     *
     * @param claim name of a claim
     * @return claim value if present
     */
    public Optional<JsonValue> payloadClaimValue(String claim) {
        JsonValue rawValue = payloadClaims.get(claim);

        if (claim.equals(AUDIENCE)) {
            return Optional.ofNullable(ensureJsonArray(rawValue));
        }
        return Optional.ofNullable(rawValue);
    }

    private JsonValue ensureJsonArray(JsonValue rawValue) {
        if (rawValue instanceof JsonArray) {
            return rawValue;
        }

        return JsonArray.create(rawValue);
    }

    /**
     * Headers.
     *
     * @return JWT headers information
     */
    public JwtHeaders headers() {
        return headers;
    }

    /**
     * All payload claims in raw json form.
     *
     * @return map of payload names to claims
     */
    public Map<String, JsonValue> payloadClaimsJson() {
        return Collections.unmodifiableMap(payloadClaims);
    }

    /**
     * Algorithm claim.
     *
     * @return algorithm or empty if claim is not defined
     */
    public Optional<String> algorithm() {
        return headers.algorithm();
    }

    /**
     * Key id claim.
     *
     * @return key id or empty if claim is not defined
     */
    public Optional<String> keyId() {
        return headers.keyId();
    }

    /**
     * Type claim.
     *
     * @return type or empty if claim is not defined
     */
    public Optional<String> type() {
        return headers.type();
    }

    /**
     * Content type claim.
     *
     * @return content type or empty if claim is not defined
     */
    public Optional<String> contentType() {
        return headers.contentType();
    }

    /**
     * Issuer claim.
     *
     * @return Issuer or empty if claim is not defined
     */
    public Optional<String> issuer() {
        return issuer;
    }

    /**
     * Expiration time claim.
     *
     * @return expiration time or empty if claim is not defined
     */
    public Optional<Instant> expirationTime() {
        return expirationTime;
    }

    /**
     * Issue time claim.
     *
     * @return issue time or empty if claim is not defined
     */
    public Optional<Instant> issueTime() {
        return issueTime;
    }

    /**
     * Not before claim.
     *
     * @return not before or empty if claim is not defined
     */
    public Optional<Instant> notBefore() {
        return notBefore;
    }

    /**
     * Subject claim.
     *
     * @return subject or empty if claim is not defined
     */
    public Optional<String> subject() {
        return subject;
    }

    /**
     * User principal claim ("upn" from microprofile specification).
     *
     * @return user principal or empty if claim is not defined
     */
    public Optional<String> userPrincipal() {
        return userPrincipal;
    }

    /**
     * User groups claim ("groups" from microprofile specification).
     *
     * @return groups or empty if claim is not defined
     */
    public Optional<List<String>> userGroups() {
        return userGroups.map(Collections::unmodifiableList);
    }

    /**
     * Audience claim.
     *
     * @return audience or empty if claim is not defined
     */
    public Optional<List<String>> audience() {
        return audience;
    }

    /**
     * Jwt id claim.
     *
     * @return jwt id or empty if claim is not defined
     */
    public Optional<String> jwtId() {
        return jwtId;
    }

    /**
     * Email claim.
     *
     * @return email or empty if claim is not defined
     */
    public Optional<String> email() {
        return email;
    }

    /**
     * Email verified claim.
     *
     * @return email verified or empty if claim is not defined
     */
    public Optional<Boolean> emailVerified() {
        return emailVerified;
    }

    /**
     * Full name claim.
     *
     * @return full name or empty if claim is not defined
     */
    public Optional<String> fullName() {
        return fullName;
    }

    /**
     * Given name claim.
     *
     * @return given name or empty if claim is not defined
     */
    public Optional<String> givenName() {
        return givenName;
    }

    /**
     * Middle name claim.
     *
     * @return middle name or empty if claim is not defined
     */
    public Optional<String> middleName() {
        return middleName;
    }

    /**
     * Family name claim.
     *
     * @return family name or empty if claim is not defined
     */
    public Optional<String> familyName() {
        return familyName;
    }

    /**
     * Locale claim.
     *
     * @return locale or empty if claim is not defined
     */
    public Optional<Locale> locale() {
        return locale;
    }

    /**
     * Nickname claim.
     *
     * @return nickname or empty if claim is not defined
     */
    public Optional<String> nickname() {
        return nickname;
    }

    /**
     * Preferred username claim.
     *
     * @return preferred username or empty if claim is not defined
     */
    public Optional<String> preferredUsername() {
        return preferredUsername;
    }

    /**
     * Profile URI claim.
     *
     * @return profile URI or empty if claim is not defined
     */
    public Optional<URI> profile() {
        return profile;
    }

    /**
     * Picture URI claim.
     *
     * @return picture URI or empty if claim is not defined
     */
    public Optional<URI> picture() {
        return picture;
    }

    /**
     * Website URI claim.
     *
     * @return website URI or empty if claim is not defined
     */
    public Optional<URI> website() {
        return website;
    }

    /**
     * Gender claim.
     *
     * @return gender or empty if claim is not defined
     */
    public Optional<String> gender() {
        return gender;
    }

    /**
     * Birthday claim.
     *
     * @return birthday or empty if claim is not defined
     */
    public Optional<LocalDate> birthday() {
        return birthday;
    }

    /**
     * Time Zone claim.
     *
     * @return time zone or empty if claim is not defined
     */
    public Optional<ZoneId> timeZone() {
        return timeZone;
    }

    /**
     * Phone number claim.
     *
     * @return phone number or empty if claim is not defined
     */
    public Optional<String> phoneNumber() {
        return phoneNumber;
    }

    /**
     * Phone number verified claim.
     *
     * @return phone number verified or empty if claim is not defined
     */
    public Optional<Boolean> phoneNumberVerified() {
        return phoneNumberVerified;
    }

    /**
     * Updated at claim.
     *
     * @return updated at or empty if claim is not defined
     */
    public Optional<Instant> updatedAt() {
        return updatedAt;
    }

    /**
     * Address claim.
     *
     * @return address or empty if claim is not defined
     */
    public Optional<JwtUtil.Address> address() {
        return address;
    }

    /**
     * AtHash claim.
     *
     * @return atHash or empty if claim is not defined
     */
    public Optional<byte[]> atHash() {
        return atHash;
    }

    /**
     * CHash claim.
     *
     * @return cHash or empty if claim is not defined
     */
    public Optional<byte[]> cHash() {
        return cHash;
    }

    /**
     * Nonce claim.
     *
     * @return nonce or empty if claim is not defined
     */
    public Optional<String> nonce() {
        return nonce;
    }

    /**
     * Create a JSON header object.
     *
     * @return JsonObject for header
     */
    public JsonObject headerJsonObject() {
        return headers.headerJsonObject();
    }

    /**
     * Create a JSON payload object.
     *
     * @return JsonObject for payload
     */
    public JsonObject payloadJsonObject() {
        JsonObject.Builder objectBuilder = JsonObject.builder();
        payloadClaims.forEach(objectBuilder::set);

        // known payload
        this.issuer.ifPresent(it -> objectBuilder.set(ISSUER, it));
        this.expirationTime.ifPresent(it -> objectBuilder.set(EXPIRATION, it.getEpochSecond()));
        this.issueTime.ifPresent(it -> objectBuilder.set(ISSUED_AT, it.getEpochSecond()));
        this.notBefore.ifPresent(it -> objectBuilder.set(NOT_BEFORE, it.getEpochSecond()));
        this.subject.ifPresent(it -> objectBuilder.set(SUBJECT, it));
        this.userPrincipal.ifPresent(it -> objectBuilder.set(USER_PRINCIPAL, it));
        this.userGroups.ifPresent(it -> objectBuilder.set(USER_GROUPS, JsonArray.createStrings(it)));
        this.audience.ifPresent(it -> objectBuilder.set(AUDIENCE, JsonArray.createStrings(it)));
        this.jwtId.ifPresent(it -> objectBuilder.set(JWT_ID, it));
        this.email.ifPresent(it -> objectBuilder.set(EMAIL, it));
        this.emailVerified.ifPresent(it -> objectBuilder.set(EMAIL_VERIFIED, it));
        this.fullName.ifPresent(it -> objectBuilder.set(FULL_NAME, it));
        this.givenName.ifPresent(it -> objectBuilder.set(GIVEN_NAME, it));
        this.middleName.ifPresent(it -> objectBuilder.set(MIDDLE_NAME, it));
        this.familyName.ifPresent(it -> objectBuilder.set(FAMILY_NAME, it));
        this.locale.ifPresent(it -> objectBuilder.set(LOCALE, it.toLanguageTag()));
        this.nickname.ifPresent(it -> objectBuilder.set(NICKNAME, it));
        this.preferredUsername.ifPresent(it -> objectBuilder.set(PREFERRED_USERNAME, it));
        this.profile.ifPresent(it -> objectBuilder.set(PROFILE, it.toASCIIString()));
        this.picture.ifPresent(it -> objectBuilder.set(PICTURE, it.toASCIIString()));
        this.website.ifPresent(it -> objectBuilder.set(WEBSITE, it.toASCIIString()));
        this.gender.ifPresent(it -> objectBuilder.set(GENDER, it));
        this.birthday.ifPresent(it -> objectBuilder.set(BIRTHDAY, JwtUtil.toDate(it)));
        this.timeZone.ifPresent(it -> objectBuilder.set(ZONE_INFO, it.getId()));
        this.phoneNumber.ifPresent(it -> objectBuilder.set(PHONE_NUMBER, it));
        this.phoneNumberVerified.ifPresent(it -> objectBuilder.set(PHONE_NUMBER_VERIFIED, it));
        this.updatedAt.ifPresent(it -> objectBuilder.set(UPDATED_AT, it.getEpochSecond()));
        this.address.ifPresent(it -> objectBuilder.set(ADDRESS, it.jsonObject()));
        this.atHash.ifPresent(it -> objectBuilder.set(AT_HASH, JwtUtil.base64Url(it)));
        this.cHash.ifPresent(it -> objectBuilder.set(C_HASH, JwtUtil.base64Url(it)));
        this.nonce.ifPresent(it -> objectBuilder.set(NONCE, it));

        this.scopes.ifPresent(it -> objectBuilder.set(SCOPE, String.join(" ", it)));

        return objectBuilder.build();
    }

    /**
     * Builder of a {@link Jwt}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, Jwt> {
        private final JwtHeaders.Builder headerBuilder = JwtHeaders.builder();
        private final Map<String, Object> payloadClaims = new HashMap<>();
        private Optional<String> issuer = Optional.empty();
        private Optional<Instant> expirationTime = Optional.empty();
        private Optional<Instant> issueTime = Optional.empty();
        private Optional<Instant> notBefore = Optional.empty();
        private Optional<String> subject = Optional.empty();
        private Optional<String> userPrincipal = Optional.empty();
        private Optional<List<String>> userGroups = Optional.empty();
        private List<String> audience;
        private Optional<String> jwtId = Optional.empty();
        private Optional<String> email = Optional.empty();
        private Optional<Boolean> emailVerified = Optional.empty();
        private Optional<String> fullName = Optional.empty();
        private Optional<String> givenName = Optional.empty();
        private Optional<String> middleName = Optional.empty();
        private Optional<String> familyName = Optional.empty();
        private Optional<Locale> locale = Optional.empty();
        private Optional<String> nickname = Optional.empty();
        private Optional<String> preferredUsername = Optional.empty();
        private Optional<URI> profile = Optional.empty();
        private Optional<URI> picture = Optional.empty();
        private Optional<URI> website = Optional.empty();
        private Optional<String> gender = Optional.empty();
        private Optional<LocalDate> birthday = Optional.empty();
        private Optional<ZoneId> timeZone = Optional.empty();
        private Optional<String> phoneNumber = Optional.empty();
        private Optional<Boolean> phoneNumberVerified = Optional.empty();
        private Optional<Instant> updatedAt = Optional.empty();
        private Optional<JwtUtil.Address> address = Optional.empty();
        private Optional<byte[]> atHash = Optional.empty();
        private Optional<byte[]> cHash = Optional.empty();
        private Optional<String> nonce = Optional.empty();
        private Optional<List<String>> scopes = Optional.empty();

        private Builder() {
        }

        /**
         * Key id to be used to sign/verify this JWT.
         *
         * @param keyId key id (pointing to a JWK)
         * @return updated builder instance
         */
        public Builder keyId(String keyId) {
            headerBuilder.keyId(keyId);
            return this;
        }

        /**
         * Type of this JWT.
         *
         * @param type type definition (JWT, JWE)
         * @return updated builder instance
         */
        public Builder type(String type) {
            headerBuilder.type(type);
            return this;
        }

        /**
         * OAuth2 scope claims to set.
         *
         * @param scopes scope claims to add to a JWT
         * @return update builder instance
         */
        public Builder scopes(List<String> scopes) {
            List<String> list = new LinkedList<>(scopes);
            this.scopes = Optional.of(list);
            return this;
        }

        /**
         * OAuth2 scope claim to add.
         *
         * @param scope scope claim to add to a JWT
         * @return updated builder instance
         */
        public Builder addScope(String scope) {
            this.scopes = this.scopes.or(() -> Optional.of(new LinkedList<>()));
            this.scopes.ifPresent(it -> it.add(scope));
            return this;
        }

        /**
         * A user group claim to add.
         * Based on Microprofile JWT Auth specification, uses claim "groups".
         *
         * @param group group name to add to the list of groups
         * @return updated builder instance
         */
        public Builder addUserGroup(String group) {
            this.userGroups = this.userGroups.or(() -> Optional.of(new LinkedList<>()));
            this.userGroups.ifPresent(it -> it.add(group));
            return this;
        }

        /**
         * This header claim should only be used when nesting or encrypting JWT.
         * See <a href="https://tools.ietf.org/html/rfc7519#section-5.2">RFC 7519, section 5.2</a>.
         *
         * @param contentType content type to use, use "JWT" if nested
         * @return updated builder instance
         */
        public Builder contentType(String contentType) {
            headerBuilder.contentType(contentType);
            return this;
        }

        /**
         * Add a generic header claim.
         *
         * @param claim claim to add
         * @param value value of the header claim
         * @return updated builder instance
         */
        public Builder addHeaderClaim(String claim, Object value) {
            headerBuilder.addHeaderClaim(claim, value);
            return this;
        }

        private void addClaim(Map<String, Object> claims, String claim, Object value) {
            claims.put(claim, value);
        }

        /**
         * Add a generic payload claim.
         *
         * @param claim claim to add
         * @param value value of the payload claim
         * @return updated builder instance
         */
        public Builder addPayloadClaim(String claim, Object value) {
            addClaim(payloadClaims, claim, value);
            return this;
        }

        /**
         * The "alg" claim is used to define the signature algorithm.
         * Note that this algorithm should be the same as is supported by
         * the JWK used to sign (or verify) the JWT.
         *
         * @param algorithm algorithm to use, {@link Jwk#ALG_NONE} for none
         * @return updated builder instance
         */
        public Builder algorithm(String algorithm) {
            headerBuilder.algorithm(algorithm);
            return this;
        }

        /**
         * The issuer claim identifies the principal that issued the JWT.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.1">RFC 7519, section 4.1.1</a>.
         *
         * @param issuer issuer name or URL
         * @return updated builder instance
         */
        public Builder issuer(String issuer) {
            this.issuer = Optional.ofNullable(issuer);
            return this;
        }

        /**
         * The expiration time defines the time that this JWT loses validity.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.4">RFC 7519, section 4.1.4</a>.
         *
         * @param expirationTime when this JWT expires
         * @return updated builder instance
         */
        public Builder expirationTime(Instant expirationTime) {
            this.expirationTime = Optional.ofNullable(expirationTime);
            return this;
        }

        /**
         * The issue time defines the time that this JWT was issued.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.6">RFC 7519, section 4.1.6</a>.
         *
         * @param issueTime when this JWT was created
         * @return updated builder instance
         */
        public Builder issueTime(Instant issueTime) {
            this.issueTime = Optional.ofNullable(issueTime);
            return this;
        }

        /**
         * The not before time defines the time that this JWT starts being valid.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.5">RFC 7519, section 4.1.5</a>.
         *
         * @param notBefore JWT is not valid before this time
         * @return updated builder instance
         */
        public Builder notBefore(Instant notBefore) {
            this.notBefore = Optional.ofNullable(notBefore);
            return this;
        }

        /**
         * Subject defines the principal this JWT was issued for (e.g. user id).
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.2">RFC 7519, section 4.1.2</a>.
         *
         * @param subject subject of this JWt
         * @return updated builder instance
         */
        public Builder subject(String subject) {
            this.subject = Optional.ofNullable(subject);
            return this;
        }

        /**
         * User principal claim as defined by Microprofile JWT Auth spec.
         * Uses "upn" claim.
         *
         * @param principal name of the principal, falls back to {@link #preferredUsername(String)} and then to
         *                  {@link #subject(String)}
         * @return updated builder instance
         */
        public Builder userPrincipal(String principal) {
            this.userPrincipal = Optional.ofNullable(principal);
            return this;
        }

        /**
         * Audience identifies the expected recipients of this JWT (optional).
         * Multiple audience may be added
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.3">RFC 7519, section 4.1.3</a>.
         *
         * @param audience audience of this JWT
         * @return updated builder instance
         */
        public Builder addAudience(String audience) {
            if (this.audience == null) {
                this.audience = new LinkedList<>();
            }
            this.audience.add(audience);
            return this;
        }

        /**
         * Audience identifies the expected recipients of this JWT (optional).
         * Replaces existing configured audiences.
         * This configures audience in header claims, usually this is defined in payload.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.3">RFC 7519, section 4.1.3</a>.
         *
         * @param audience audience of this JWT
         * @return updated builder instance
         */
        public Builder audience(List<String> audience) {
            this.audience = new LinkedList<>(audience);
            return this;
        }

        /**
         * A unique identifier of this JWT (optional) - must be unique across issuers.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.7">RFC 7519, section 4.1.7</a>.
         *
         * @param jwtId unique identifier
         * @return updated builder instance
         */
        public Builder jwtId(String jwtId) {
            this.jwtId = Optional.ofNullable(jwtId);
            return this;
        }

        /**
         * Email claim.
         *
         * @param email email claim for this JWT's subject
         * @return updated builder instance
         */
        public Builder email(String email) {
            this.email = Optional.ofNullable(email);
            return this;
        }

        /**
         * Claim defining whether e-mail is verified or not.
         *
         * @param emailVerified true if verified
         * @return updated builder instance
         */
        public Builder emailVerified(Boolean emailVerified) {
            this.emailVerified = Optional.ofNullable(emailVerified);
            return this;
        }

        /**
         * Full name of subject.
         *
         * @param fullName full name of the subject
         * @return updated builder instance
         */
        public Builder fullName(String fullName) {
            this.fullName = Optional.ofNullable(fullName);
            return this;
        }

        /**
         * Given name of subject (first name).
         *
         * @param givenName given name of the subject
         * @return updated builder instance
         */
        public Builder givenName(String givenName) {
            this.givenName = Optional.ofNullable(givenName);
            return this;
        }

        /**
         * Middle name of subject.
         *
         * @param middleName middle name of the subject
         * @return updated builder instance
         */
        public Builder middleName(String middleName) {
            this.middleName = Optional.ofNullable(middleName);
            return this;
        }

        /**
         * Family name of subject (surname).
         *
         * @param familyName family name of the subject
         * @return updated builder instance
         */
        public Builder familyName(String familyName) {
            this.familyName = Optional.ofNullable(familyName);
            return this;
        }

        /**
         * Locale of the subject.
         *
         * @param locale locale to use
         * @return updated builder instance
         */
        public Builder locale(Locale locale) {
            this.locale = Optional.ofNullable(locale);
            return this;
        }

        /**
         * Nickname of the subject.
         *
         * @param nickname nickname
         * @return updated builder instance
         */
        public Builder nickname(String nickname) {
            this.nickname = Optional.ofNullable(nickname);
            return this;
        }

        /**
         * Preferred username of the subject.
         *
         * @param preferredUsername username to view
         * @return updated builder instance
         */
        public Builder preferredUsername(String preferredUsername) {
            this.preferredUsername = Optional.ofNullable(preferredUsername);
            return this;
        }

        /**
         * Profile URI of the subject.
         *
         * @param profile link to profile of subject
         * @return updated builder instance
         */
        public Builder profile(URI profile) {
            this.profile = Optional.ofNullable(profile);
            return this;
        }

        /**
         * Profile picture URI of the subject.
         *
         * @param picture link to picture of subject
         * @return updated builder instance
         */
        public Builder picture(URI picture) {
            this.picture = Optional.ofNullable(picture);
            return this;
        }

        /**
         * Website URI of the subject.
         *
         * @param website link to website of subject
         * @return updated builder instance
         */
        public Builder website(URI website) {
            this.website = Optional.ofNullable(website);
            return this;
        }

        /**
         * Gender of the subject.
         * As this is an extension (e.g. a custom claim) used by some of the
         * issuers, the content may be arbitrary, though base values are male and female.
         *
         * @param gender gender to use
         * @return updated builder instance
         */
        public Builder gender(String gender) {
            this.gender = Optional.ofNullable(gender);
            return this;
        }

        /**
         * Birthday of the subject.
         *
         * @param birthday birthday
         * @return updated builder instance
         */
        public Builder birthday(LocalDate birthday) {
            this.birthday = Optional.ofNullable(birthday);
            return this;
        }

        /**
         * Time zone of the subject.
         *
         * @param timeZone time zone
         * @return updated builder instance
         */
        public Builder timeZone(ZoneId timeZone) {
            this.timeZone = Optional.ofNullable(timeZone);
            return this;
        }

        /**
         * Phone number of the subject.
         *
         * @param phoneNumber phone number
         * @return updated builder instance
         */
        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = Optional.ofNullable(phoneNumber);
            return this;
        }

        /**
         * Whether the phone number is verified or not.
         *
         * @param phoneNumberVerified true if number is verified
         * @return updated builder instance
         */
        public Builder phoneNumberVerified(Boolean phoneNumberVerified) {
            this.phoneNumberVerified = Optional.ofNullable(phoneNumberVerified);
            return this;
        }

        /**
         * Last time the subject's record was updated.
         *
         * @param updatedAt instant of update
         * @return updated builder instance
         */
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = Optional.ofNullable(updatedAt);
            return this;
        }

        /**
         * Address of the subject.
         *
         * @param address address to use
         * @return updated builder instance
         */
        public Builder address(JwtUtil.Address address) {
            this.address = Optional.ofNullable(address);
            return this;
        }

        /**
         * Access Token hash value. Its value is the bytes of the left-most half of the hash of the octets of the
         * ASCII representation of the access_token value, where the hash algorithm used is the hash algorithm used in the
         * alg Header Parameter of the ID Token's JOSE Header. For instance, if the alg is RS256, hash the access_token value
         * with SHA-256, then take the left-most 128 bits and set them here.
         * If the ID Token is issued from the Authorization Endpoint with an access_token value, which is the case for the
         * response_type value code id_token token, this is REQUIRED; otherwise, its inclusion is OPTIONAL.
         *
         * See <a href="http://openid.net/specs/openid-connect-core-1_0.html#CodeIDToken">OIDC 1.0 section 3.1.3.6</a>.
         *
         * @param atHash hash to use (explicit). If not defined, it will be computed if needed.
         * @return updated builder instance
         */
        public Builder atHash(byte[] atHash) {
            this.atHash = Optional.ofNullable(atHash);
            return this;
        }

        /**
         * Code hash value. Its value is the bytes of the left-most half of the hash of the octets of the ASCII
         * representation of the code value, where the hash algorithm used is the hash algorithm used in the alg Header Parameter
         * of the ID Token's JOSE Header. For instance, if the alg is HS512, hash the code value with SHA-512, then take the
         * left-most 256 bits.
         * If the ID Token is issued from the Authorization Endpoint with a code, which is the case for the response_type values
         * code id_token and code id_token token, this is REQUIRED; otherwise, its inclusion is OPTIONAL.
         *
         * @param cHash hash bytes (explicit). If not defined, it will be computed if needed.
         * @return updated builder instance
         */
        public Builder cHash(byte[] cHash) {
            this.cHash = Optional.ofNullable(cHash);
            return this;
        }

        /**
         * Nonce value is used to prevent replay attacks and must be returned if it was sent in authentication request.
         *
         * @param nonce nonce value
         * @return updated builder instance
         */
        public Builder nonce(String nonce) {
            this.nonce = Optional.ofNullable(nonce);
            return this;
        }

        /**
         * Allows configuration of JWT headers directly over the {@link JwtHeaders.Builder}.
         *
         * @param consumer header builder consumer
         * @return updated builder instance
         */
        public Builder headerBuilder(Consumer<JwtHeaders.Builder> consumer) {
            consumer.accept(headerBuilder);
            return this;
        }

        /**
         * Build and instance of the {@link Jwt}.
         *
         * @return a new token instance
         */
        @Override
        public Jwt build() {
            return new Jwt(this);
        }

        /**
         * Remove a payload claim by its name.
         *
         * @param name name of the claim to remove
         * @return updated builder instance
         */
        public Builder removePayloadClaim(String name) {
            this.payloadClaims.remove(name);
            return this;
        }
    }

}
