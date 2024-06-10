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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.Errors;

import jakarta.json.JsonString;

/**
 * Validator used for JWT claim validation.
 */
public final class JwtValidator {

    private final List<ClaimValidator> claimValidators;

    private JwtValidator(Builder builder) {
        claimValidators = List.copyOf(builder.claimValidators);
    }

    /**
     * Create new {@link JwtValidator} instance.
     * This instance will have the following validators preconfigured:
     * <ul>
     *     <li>{@link IssueTimeValidator} with expected issuer</li>
     *     <li>{@link CriticalValidator}</li>
     *     <li>{@link UserPrincipalValidator}</li>
     *     <li>{@link ExpirationValidator}</li>
     *     <li>Issuer name validator - if provided</li>
     *     <li>{@link NotBeforeValidator}</li>
     *     <li>{@link AudienceValidator} if marked for validation</li>
     * </ul>
     *
     * @param expectedIssuer expected issuer of the JWT
     * @param audience       expected audience
     * @param checkAudience  whether to check audience
     * @return new JwtValidator instance
     */
    public static JwtValidator createWithDefaults(String expectedIssuer, Set<String> audience, boolean checkAudience) {
        Builder validatorBuilder = builder()
                .addDefaultTimeValidators()
                .addCriticalValidator()
                .addUserPrincipalValidator();
        if (expectedIssuer != null) {
            validatorBuilder.addIssuerValidator(expectedIssuer);
        }
        if (checkAudience && audience != null) {
            validatorBuilder.addAudienceValidator(audience);
        }
        return validatorBuilder.build();
    }

    /**
     * Return a new Builder of the {@link JwtValidator}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validate configured validators against provided {@link Jwt}.
     *
     * @param jwt JWT to validate
     * @return errors instance to check if valid and access error messages
     */
    public Errors validate(Jwt jwt) {
        Errors.Collector collector = Errors.collector();
        claimValidators.forEach(claimValidator -> claimValidator.validate(jwt, collector, claimValidators));
        return collector.collect();
    }

    /**
     * Builder of the {@link JwtValidator}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, JwtValidator> {

        private final List<ClaimValidator> claimValidators = new ArrayList<>();

        private Builder() {
        }

        @Override
        public JwtValidator build() {
            return new JwtValidator(this);
        }

        /**
         * Add {@link ClaimValidator} instance.
         *
         * @param claimValidator claim validator to be added
         * @return updated builder instance
         */
        public Builder addClaimValidator(ClaimValidator claimValidator) {
            claimValidators.add(claimValidator);
            return this;
        }

        /**
         * Add {@link Validator} instance among the claim validators.
         * This {@link Validator} is not bound to any specific claim in the JWT scopes.
         * Default scope is set to {@link JwtScope#PAYLOAD}.
         * <p>
         * If it needs to be bound to any specific claim/JWT scope, please use {@link #addValidator(Consumer)}.
         *
         * @param validator to be added
         * @return updated builder instance
         */
        public Builder addValidator(Validator<Jwt> validator) {
            claimValidators.add(ValidatorWrapper.builder().validator(validator).build());
            return this;
        }

        /**
         * Add {@link Validator} instance among the claim validators.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addValidator(Consumer<ValidatorWrapper.Builder> builderConsumer) {
            ValidatorWrapper.Builder builder = ValidatorWrapper.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new {@link FieldValidator} of the header field.
         *
         * @param claimKey      claim key
         * @param fieldName     header field to be validated
         * @param expectedValue expected value of the field
         * @return updated builder instance
         */
        public Builder addHeaderFieldValidator(String claimKey, String fieldName, String expectedValue) {
            claimValidators.add(FieldValidator.builder()
                                        .scope(JwtScope.HEADER)
                                        .claimKey(claimKey)
                                        .name(fieldName)
                                        .expectedValue(expectedValue)
                                        .build());
            return this;
        }

        /**
         * Add new {@link FieldValidator} of the payload field.
         *
         * @param claimKey      claim key
         * @param fieldName     payload field to be validated
         * @param expectedValue expected value of the field
         * @return updated builder instance
         */
        public Builder addPayloadFieldValidator(String claimKey, String fieldName, String expectedValue) {
            claimValidators.add(FieldValidator.builder()
                                        .claimKey(claimKey)
                                        .name(fieldName)
                                        .expectedValue(expectedValue)
                                        .build());
            return this;
        }

        /**
         * Add new {@link FieldValidator} based on the builder configuration.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addFieldValidator(Consumer<FieldValidator.Builder> builderConsumer) {
            FieldValidator.Builder builder = FieldValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new JWT issuer validator.
         * This issuer claim is set as mandatory to be present by default.
         *
         * @param expectedIssuer expected JWT issuer
         * @return updated builder instance
         */
        public Builder addIssuerValidator(String expectedIssuer) {
            return addIssuerValidator(expectedIssuer, true);
        }

        /**
         * Add new JWT issuer validator.
         *
         * @param expectedIssuer expected JWT issuer
         * @param mandatory      whether this issuer claim is mandatory
         * @return updated builder instance
         */
        public Builder addIssuerValidator(String expectedIssuer, boolean mandatory) {
            claimValidators.add(FieldValidator.builder()
                                        .fieldAccessor(Jwt::issuer)
                                        .name("Issuer")
                                        .expectedValue(expectedIssuer)
                                        .mandatory(mandatory)
                                        .build());
            return this;
        }

        /**
         * Add default time validators.
         * This adds the following validators:
         * <ul>
         *     <li>{@link ExpirationValidator}</li>
         *     <li>{@link IssueTimeValidator}</li>
         *     <li>{@link NotBeforeValidator}</li>
         * </ul>
         *
         * @return updated builder instance
         */
        public Builder addDefaultTimeValidators() {
            addExpirationValidator();
            addIssueTimeValidator();
            addNotBeforeValidator();
            return this;
        }

        /**
         * Add default time validators with specific time settings.
         * This adds the following validators:
         * <ul>
         *     <li>{@link ExpirationValidator}</li>
         *     <li>{@link IssueTimeValidator}</li>
         *     <li>{@link NotBeforeValidator}</li>
         * </ul>
         *
         * @param now             time which is considered current time during the time validation
         * @param allowedTimeSkew allowed time skew
         * @param mandatory       whether the time claims are mandatory to be present
         * @return updated builder instance
         */
        public Builder addDefaultTimeValidators(Instant now, Duration allowedTimeSkew, boolean mandatory) {
            addExpirationValidator(builder -> builder.now(now).allowedTimeSkew(allowedTimeSkew).mandatory(mandatory));
            addIssueTimeValidator(builder -> builder.now(now).allowedTimeSkew(allowedTimeSkew).mandatory(mandatory));
            addNotBeforeValidator(builder -> builder.now(now).allowedTimeSkew(allowedTimeSkew).mandatory(mandatory));
            return this;
        }

        /**
         * Add new {@link ExpirationValidator} instance.
         *
         * @return updated builder instance
         */
        public Builder addExpirationValidator() {
            claimValidators.add(ExpirationValidator.builder().build());
            return this;
        }

        /**
         * Add new {@link ExpirationValidator} instance based on the builder configuration.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addExpirationValidator(Consumer<ExpirationValidator.Builder> builderConsumer) {
            ExpirationValidator.Builder builder = ExpirationValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new {@link NotBeforeValidator} instance.
         *
         * @return updated builder instance
         */
        public Builder addNotBeforeValidator() {
            claimValidators.add(NotBeforeValidator.builder().build());
            return this;
        }

        /**
         * Add new {@link NotBeforeValidator} instance based on the builder configuration.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addNotBeforeValidator(Consumer<NotBeforeValidator.Builder> builderConsumer) {
            NotBeforeValidator.Builder builder = NotBeforeValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new {@link IssueTimeValidator} instance.
         *
         * @return updated builder instance
         */
        public Builder addIssueTimeValidator() {
            claimValidators.add(IssueTimeValidator.builder().build());
            return this;
        }

        /**
         * Add new {@link IssueTimeValidator} instance based on the builder configuration.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addIssueTimeValidator(Consumer<IssueTimeValidator.Builder> builderConsumer) {
            IssueTimeValidator.Builder builder = IssueTimeValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new "crit" claim validator.
         * This validator behaves as mentioned in RFC 7515 - 4.1.11.
         *
         * @return updated builder instance
         * @see <a href="https://datatracker.ietf.org/doc/html/rfc7515#section-4.1.11">RFC 7515 - 4.1.11</a>
         */
        public Builder addCriticalValidator() {
            claimValidators.add(new CriticalValidator());
            return this;
        }

        /**
         * Add new {@link UserPrincipalValidator}.
         *
         * @return updated builder instance
         */
        public Builder addUserPrincipalValidator() {
            claimValidators.add(UserPrincipalValidator.builder().build());
            return this;
        }

        /**
         * Add new {@link UserPrincipalValidator} instance based on the builder configuration.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addUserPrincipalValidator(Consumer<UserPrincipalValidator.Builder> builderConsumer) {
            UserPrincipalValidator.Builder builder = UserPrincipalValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new {@link MaxTokenAgeValidator} with the expected max token age.
         *
         * @param expectedMaxTokenAge expected max token age
         * @return updated builder instance
         */
        public Builder addMaxTokenAgeValidator(Duration expectedMaxTokenAge) {
            claimValidators.add(MaxTokenAgeValidator.builder()
                                        .expectedMaxTokenAge(expectedMaxTokenAge)
                                        .build());
            return this;
        }

        /**
         * Add new {@link MaxTokenAgeValidator} instance based on the builder configuration.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addMaxTokenAgeValidator(Consumer<MaxTokenAgeValidator.Builder> builderConsumer) {
            MaxTokenAgeValidator.Builder builder = MaxTokenAgeValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new {@link AudienceValidator} with the expected audience.
         *
         * @param expectedAudience expected audience
         * @return updated builder instance
         */
        public Builder addAudienceValidator(String expectedAudience) {
            claimValidators.add(AudienceValidator.builder()
                                        .addExpectedAudience(expectedAudience)
                                        .build());
            return this;
        }

        /**
         * Add new {@link AudienceValidator} with the expected audience.
         *
         * @param expectedAudience expected audience
         * @return updated builder instance
         */
        public Builder addAudienceValidator(Set<String> expectedAudience) {
            claimValidators.add(AudienceValidator.builder()
                                        .expectedAudience(expectedAudience)
                                        .build());
            return this;
        }

        /**
         * Add new {@link AudienceValidator} instance based on the builder configuration.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addAudienceValidator(Consumer<AudienceValidator.Builder> builderConsumer) {
            AudienceValidator.Builder builder = AudienceValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Clear all add validators.
         *
         * @return updated builder instance
         */
        public Builder clearValidators() {
            claimValidators.clear();
            return this;
        }
    }

    private abstract static class CommonValidator implements ClaimValidator {

        private final JwtScope scope;
        private final Set<String> claims;

        private CommonValidator(BaseBuilder<?, ?> builder) {
            this.scope = builder.scope;
            this.claims = Set.copyOf(builder.claims);
        }

        @Override
        public JwtScope jwtScope() {
            return scope;
        }

        @Override
        public Set<String> claims() {
            return claims;
        }

        abstract static class BaseBuilder<B extends BaseBuilder<B, T>, T>
                implements io.helidon.common.Builder<BaseBuilder<B, T>, T> {

            private JwtScope scope = JwtScope.PAYLOAD;
            private Set<String> claims = new HashSet<>();

            private BaseBuilder() {
            }

            /**
             * The scope of JWT.
             * Default value is {@link JwtScope#PAYLOAD}.
             *
             * @param scope jwt scope
             * @return updated builder instance
             */
            B scope(JwtScope scope) {
                this.scope = Objects.requireNonNull(scope);
                return me();
            }

            /**
             * Add JWT claim this validator is bound to.
             *
             * @param claim claim name
             * @return updated builder instance
             */
            B addClaim(String claim) {
                this.claims.add(claim);
                return me();
            }

            /**
             * Add JWT claim this validator is bound to.
             *
             * @param claims bound claims
             * @return updated builder instance
             */
            B claims(Set<String> claims) {
                this.claims = new HashSet<>(claims);
                return me();
            }

            /**
             * Clear all set claims.
             *
             * @return updated builder instance
             */
            B clearClaims() {
                this.claims.clear();
                return me();
            }

            /**
             * Currently set {@link JwtScope} scope value.
             *
             * @return scope value
             */
            JwtScope scope() {
                return scope;
            }

            @SuppressWarnings("unchecked")
            protected B me() {
                return (B) this;
            }
        }
    }

    private abstract static class OptionalValidator extends CommonValidator {
        private final boolean mandatory;
        private final String missingClaimMessage;

        OptionalValidator(BaseBuilder<?, ?> builder) {
            super(builder);
            this.mandatory = builder.mandatory;
            this.missingClaimMessage = builder.missingClaimMessage;
        }

        <T> Optional<T> validate(String name, Optional<T> optional, Errors.Collector collector) {
            if (mandatory && optional.isEmpty()) {
                String message;
                if (missingClaimMessage == null) {
                    message = "Field " + name + " is mandatory, yet not defined in JWT";
                } else {
                    message = missingClaimMessage;
                }
                collector.fatal(message);
            }
            return optional;
        }

        private abstract static class BaseBuilder<B extends BaseBuilder<B, T>, T> extends CommonValidator.BaseBuilder<B, T> {

            private boolean mandatory = false;
            private String missingClaimMessage;

            private BaseBuilder() {
            }

            /**
             * Whether handled claim is mandatory to be present.
             * Default value is {@code false}.
             *
             * @param mandatory mandatory to be present
             * @return updated builder instance
             */
            public B mandatory(boolean mandatory) {
                this.mandatory = mandatory;
                return me();
            }

            /**
             * Custom missing claim error message.
             *
             * @param missingClaimMessage missing claim error message
             * @return updated builder instance
             */
            public B missingClaimMessage(String missingClaimMessage) {
                this.missingClaimMessage = missingClaimMessage;
                return me();
            }
        }
    }

    private abstract static class InstantValidator extends OptionalValidator {
        private final Instant now;
        private final Duration allowedTimeSkew;

        private InstantValidator(BaseBuilder<?, ?> builder) {
            super(builder);
            this.now = builder.now;
            this.allowedTimeSkew = builder.allowedTimeSkew;
        }

        Instant latest() {
            return instant().plus(allowedTimeSkew);
        }

        Instant latest(Instant now) {
            return now.plus(allowedTimeSkew);
        }

        Instant earliest() {
            return instant().minus(allowedTimeSkew);
        }

        Instant earliest(Instant now) {
            return now.minus(allowedTimeSkew);
        }

        Instant instant() {
            return now == null ? Instant.now() : now;
        }

        abstract static class BaseBuilder<B extends BaseBuilder<B, T>, T> extends OptionalValidator.BaseBuilder<B, T> {

            private Instant now = null;
            private Duration allowedTimeSkew = Duration.ofSeconds(5);

            private BaseBuilder() {
            }

            /**
             * Specific "current" time to validate time claim against.
             * If not set, {@link Instant#now()} is used for every validation again.
             *
             * @param now specific current time
             * @return updated builder instance
             */
            public B now(Instant now) {
                this.now = now;
                return me();
            }

            /**
             * Allowed time skew for time validation.
             * The default value is 5 seconds.
             *
             * @param allowedTimeSkew allowed time skew
             * @return updated builder instance
             */
            public B allowedTimeSkew(Duration allowedTimeSkew) {
                this.allowedTimeSkew = allowedTimeSkew;
                return me();
            }
        }
    }

    /**
     * Validator of the header claim "crit".
     * Validation based on RFC7515 - 4.1.11 "crit" (Critical) Header Parameter.
     */
    public static final class CriticalValidator implements ClaimValidator {

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

    /**
     * Validator of a string field obtained from the JWT.
     */
    public static final class FieldValidator extends OptionalValidator {
        private final Function<Jwt, Optional<String>> fieldAccessor;
        private final String name;
        private final String expectedValue;

        private FieldValidator(Builder builder) {
            super(builder);
            this.name = builder.name;
            this.fieldAccessor = builder.fieldAccessor;
            this.expectedValue = builder.expectedValue;
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
            super.validate(name, fieldAccessor.apply(token), collector)
                    .ifPresent(it -> {
                        if (!expectedValue.equals(it)) {
                            collector.fatal(token,
                                            "Expected value of field \"" + name + "\" was \"" + expectedValue + "\", but "
                                                    + "actual value is: \"" + it + "\"");
                        }
                    });
        }

        /**
         * Builder of the {@link FieldValidator}.
         */
        public static final class Builder extends OptionalValidator.BaseBuilder<Builder, FieldValidator> {

            private String claimKey;
            private String name;
            private String expectedValue;
            private Function<Jwt, Optional<String>> fieldAccessor;

            private Builder() {
            }

            /**
             * Set handled claim key.
             *
             * @param claimKey supported claim key
             * @return updated builder instance
             */
            public Builder claimKey(String claimKey) {
                //This supports only one claim name
                clearClaims();
                addClaim(claimKey);
                this.claimKey = claimKey;
                return this;
            }

            /**
             * Field name value.
             *
             * @param name name of the field
             * @return updated builder instance
             */
            public Builder name(String name) {
                this.name = name;
                return this;
            }

            /**
             * Expected value to be present in the supported claim.
             *
             * @param expectedValue expected claim value
             * @return updated builder instance
             */
            public Builder expectedValue(String expectedValue) {
                this.expectedValue = expectedValue;
                return this;
            }

            /**
             * Function to extract field from JWT.
             *
             * @param fieldAccessor function to extract field from JWT
             * @return updated builder instance
             */
            public Builder fieldAccessor(Function<Jwt, Optional<String>> fieldAccessor) {
                this.fieldAccessor = fieldAccessor;
                return this;
            }

            @Override
            public Builder scope(JwtScope scope) {
                return super.scope(scope);
            }

            @Override
            public FieldValidator build() {
                if (name == null) {
                    throw new RuntimeException("Missing supported field name");
                } else if (expectedValue == null) {
                    throw new RuntimeException("Missing expected claim value");
                }
                if (fieldAccessor == null) {
                    if (claimKey == null) {
                        throw new RuntimeException("Field accessor or claim key name has to be set.");
                    }
                    if (scope() == JwtScope.PAYLOAD) {
                        fieldAccessor = jwt -> jwt.payloadClaim(claimKey).map(it -> ((JsonString) it).getString());
                    } else {
                        fieldAccessor = jwt -> jwt.headerClaim(claimKey).map(it -> ((JsonString) it).getString());
                    }
                }
                return new FieldValidator(this);
            }
        }
    }

    /**
     * Validator of the issue time claim.
     */
    public static final class IssueTimeValidator extends InstantValidator {

        private IssueTimeValidator(Builder builder) {
            super(builder);
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder().addClaim(Jwt.ISSUED_AT);
        }

        @Override
        public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
            Optional<Instant> issueTime = token.issueTime();
            issueTime.ifPresent(it -> {
                // must be issued in the past
                if (latest().isBefore(it)) {
                    collector.fatal(token, "Token was not issued in the past: " + it);
                }
            });
            // ensure we fail if mandatory and not present
            super.validate("issueTime", issueTime, collector);
        }

        /**
         * Builder of the {@link IssueTimeValidator}.
         */
        public static final class Builder extends InstantValidator.BaseBuilder<Builder, IssueTimeValidator> {

            private Builder() {
            }

            @Override
            public IssueTimeValidator build() {
                return new IssueTimeValidator(this);
            }
        }
    }

    /**
     * Validator of expiration claim.
     */
    public static final class ExpirationValidator extends InstantValidator {

        private ExpirationValidator(Builder builder) {
            super(builder);
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder()
                    .addClaim(Jwt.EXPIRATION)
                    .addClaim(Jwt.ISSUED_AT);
        }

        @Override
        public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
            Optional<Instant> expirationTime = token.expirationTime();
            expirationTime.ifPresent(it -> {
                if (earliest().isAfter(it)) {
                    collector.fatal(token, "Token no longer valid, expiration: " + it);
                }
                token.issueTime().ifPresent(issued -> {
                    if (issued.isAfter(it)) {
                        collector.fatal(token, "Token issue date is after its expiration, "
                                + "issue: " + it + ", expiration: " + it);
                    }
                });
            });
            // ensure we fail if mandatory and not present
            super.validate("expirationTime", expirationTime, collector);
        }

        /**
         * Builder of the {@link ExpirationValidator}.
         */
        public static final class Builder extends BaseBuilder<Builder, ExpirationValidator> {

            private Builder() {
            }

            @Override
            public ExpirationValidator build() {
                return new ExpirationValidator(this);
            }
        }
    }

    /**
     * Validator of not before claim.
     */
    public static final class NotBeforeValidator extends InstantValidator {

        private NotBeforeValidator(Builder builder) {
            super(builder);
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder().addClaim(Jwt.NOT_BEFORE);
        }

        @Override
        public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
            Optional<Instant> notBefore = token.notBefore();
            notBefore.ifPresent(it -> {
                if (latest().isBefore(it)) {
                    collector.fatal(token, "Token not yet valid, not before: " + it);
                }
            });
            // ensure we fail if mandatory and not present
            super.validate("notBefore", notBefore, collector);
        }

        /**
         * Builder of the {@link NotBeforeValidator}.
         */
        public static final class Builder extends BaseBuilder<Builder, NotBeforeValidator> {

            private Builder() {
            }

            @Override
            public NotBeforeValidator build() {
                return new NotBeforeValidator(this);
            }
        }
    }

    /**
     * User principal validator.
     */
    public static final class UserPrincipalValidator extends OptionalValidator {

        private UserPrincipalValidator(Builder builder) {
            super(builder);
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder()
                    .addClaim(Jwt.USER_PRINCIPAL)
                    .mandatory(true);
        }

        @Override
        public void validate(Jwt object, Errors.Collector collector, List<ClaimValidator> validators) {
            super.validate("User Principal", object.userPrincipal(), collector);
        }

        /**
         * Builder of the {@link UserPrincipalValidator}.
         */
        public static final class Builder extends OptionalValidator.BaseBuilder<Builder, UserPrincipalValidator> {

            private Builder() {
            }

            @Override
            public UserPrincipalValidator build() {
                return new UserPrincipalValidator(this);
            }
        }

    }

    /**
     * Max token age validator.
     */
    public static final class MaxTokenAgeValidator extends InstantValidator {
        private final Duration expectedMaxTokenAge;

        private MaxTokenAgeValidator(Builder builder) {
            super(builder);
            this.expectedMaxTokenAge = builder.expectedMaxTokenAge;
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder()
                    .addClaim(Jwt.ISSUED_AT)
                    .missingClaimMessage("Claim iat is required to be present in JWT when validating token max allowed age.");
        }

        @Override
        public void validate(Jwt jwt, Errors.Collector collector, List<ClaimValidator> validators) {
            Optional<Instant> maybeIssueTime = jwt.issueTime();
            maybeIssueTime.ifPresent(issueTime -> {
                Instant now = instant();
                Instant earliest = earliest(issueTime);
                Instant latest = latest(issueTime).plus(expectedMaxTokenAge);
                if (earliest.isBefore(now) && latest.isAfter(now)) {
                    return;
                }
                collector.fatal(jwt, "Current time need to be between " + earliest
                        + " and " + latest + ", but was " + now);
            });
            super.validate(Jwt.ISSUED_AT, maybeIssueTime, collector);
        }

        /**
         * Builder of the {@link MaxTokenAgeValidator}.
         */
        public static final class Builder extends InstantValidator.BaseBuilder<Builder, MaxTokenAgeValidator> {

            private Duration expectedMaxTokenAge = null;

            private Builder() {
            }

            @Override
            public MaxTokenAgeValidator build() {
                if (expectedMaxTokenAge == null) {
                    throw new RuntimeException("Expected JWT max token age is required to be set");
                }
                return new MaxTokenAgeValidator(this);
            }

            /**
             * Expected max token age.
             *
             * @param expectedMaxTokenAge max token age
             * @return updated builder instance
             */
            public Builder expectedMaxTokenAge(Duration expectedMaxTokenAge) {
                this.expectedMaxTokenAge = expectedMaxTokenAge;
                return this;
            }
        }

    }

    /**
     * Audience claim validator.
     */
    public static final class AudienceValidator extends OptionalValidator {
        private final Set<String> expectedAudience;

        private AudienceValidator(Builder builder) {
            super(builder);
            this.expectedAudience = Set.copyOf(builder.expectedAudience);
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder()
                    .addClaim(Jwt.AUDIENCE)
                    .mandatory(true);
        }

        @Override
        public void validate(Jwt jwt, Errors.Collector collector, List<ClaimValidator> validators) {
            Optional<List<String>> jwtAudiences = jwt.audience();
            jwtAudiences.ifPresent(jwtAudience -> {
                if (expectedAudience.stream().anyMatch(jwtAudiences.get()::contains)) {
                    return;
                }
                collector.fatal(jwt, "Audience must contain " + expectedAudience + ", yet it is: " + jwtAudiences);
            });
            super.validate(Jwt.AUDIENCE, jwtAudiences, collector);
        }

        /**
         * Builder of the {@link AudienceValidator}.
         */
        public static final class Builder extends OptionalValidator.BaseBuilder<Builder, AudienceValidator> {

            private Set<String> expectedAudience = new HashSet<>();

            private Builder() {
            }

            @Override
            public AudienceValidator build() {
                return new AudienceValidator(this);
            }

            /**
             * Add expected audience value.
             *
             * @param audience expected audience
             * @return updated builder instance
             */
            public Builder addExpectedAudience(String audience) {
                expectedAudience.add(audience);
                return this;
            }

            /**
             * Overwrite previously set audience with the new {@link Set} of values.
             *
             * @param expectedAudience expected audience values
             * @return updated builder instance
             */
            public Builder expectedAudience(Set<String> expectedAudience) {
                this.expectedAudience = new HashSet<>(expectedAudience);
                return this;
            }
        }
    }

    /**
     * Wrapper support for {@link Validator} instances.
     */
    public static final class ValidatorWrapper extends CommonValidator {

        private final Validator<Jwt> validator;

        private ValidatorWrapper(Builder builder) {
            super(builder);
            validator = builder.validator;
        }

        /**
         * Return a new Builder instance.
         *
         * @return new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public void validate(Jwt jwt, Errors.Collector collector, List<ClaimValidator> validators) {
            validator.validate(jwt, collector);
        }

        /**
         * Builder of the {@link ValidatorWrapper}.
         */
        public static final class Builder extends CommonValidator.BaseBuilder<Builder, ValidatorWrapper> {

            private Validator<Jwt> validator;

            private Builder() {
            }

            @Override
            public Builder claims(Set<String> claims) {
                return super.claims(claims);
            }

            @Override
            public Builder addClaim(String claim) {
                return super.addClaim(claim);
            }

            @Override
            public Builder clearClaims() {
                return super.clearClaims();
            }

            @Override
            public Builder scope(JwtScope scope) {
                return super.scope(scope);
            }

            /**
             * Instance of the {@link Validator}.
             *
             * @param validator validator instance
             * @return updated builder instance
             */
            public Builder validator(Validator<Jwt> validator) {
                this.validator = validator;
                return this;
            }

            @Override
            public ValidatorWrapper build() {
                if (validator == null) {
                    throw new RuntimeException("No required validator instance was set");
                }
                return new ValidatorWrapper(this);
            }
        }
    }
}
