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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.common.Errors;

/**
 * Validator used for JWT claim validation.
 */
public interface JwtValidator {

    /**
     * Return a new Builder of the {@link JwtValidator}.
     *
     * @return new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Validate configured validators against provided {@link Jwt}.
     *
     * @param jwt JWT to validate
     * @return errors instance to check if valid and access error messages
     */
    Errors validate(Jwt jwt);

    /**
     * Builder of the {@link JwtValidator}.
     */
    final class Builder implements io.helidon.common.Builder<Builder, JwtValidator> {

        private final List<ClaimValidator> claimValidators = new ArrayList<>();

        private Builder() {
        }

        @Override
        public JwtValidator build() {
            return new JwtValidators(this);
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
         * Default scope is set to {@link JwtScope#PAYLOAD}.
         *
         * @param validator to be added
         * @param claims    claims handled by the validator
         * @return updated builder instance
         */
        public Builder addValidator(Validator<Jwt> validator, String... claims) {
            return addValidator(JwtScope.PAYLOAD, validator, claims);
        }

        /**
         * Add {@link Validator} instance among the claim validators.
         *
         * @param scope     scope of the validator
         * @param validator to be added
         * @param claims    claims handled by the validator
         * @return updated builder instance
         */
        public Builder addValidator(JwtScope scope, Validator<Jwt> validator, String... claims) {
            Objects.requireNonNull(scope);
            Objects.requireNonNull(validator);
            claimValidators.add(ValidatorWrapper.builder()
                                        .scope(scope)
                                        .validator(validator)
                                        .claims(Set.of(claims))
                                        .build());
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
            Objects.requireNonNull(claimKey);
            Objects.requireNonNull(fieldName);
            Objects.requireNonNull(expectedValue);
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
            Objects.requireNonNull(claimKey);
            Objects.requireNonNull(fieldName);
            Objects.requireNonNull(expectedValue);
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
            Objects.requireNonNull(builderConsumer);
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
            Objects.requireNonNull(expectedIssuer);
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
            Objects.requireNonNull(now);
            Objects.requireNonNull(allowedTimeSkew);
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
            Objects.requireNonNull(builderConsumer);
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
            Objects.requireNonNull(builderConsumer);
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
            Objects.requireNonNull(builderConsumer);
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
         * This validator is mandatory by default.
         *
         * @return updated builder instance
         */
        public Builder addUserPrincipalValidator() {
            claimValidators.add(UserPrincipalValidator.builder().build());
            return this;
        }

        /**
         * Add new {@link UserPrincipalValidator} instance based on the builder configuration.
         * This validator is mandatory by default.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addUserPrincipalValidator(Consumer<UserPrincipalValidator.Builder> builderConsumer) {
            Objects.requireNonNull(builderConsumer);
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
            Objects.requireNonNull(expectedMaxTokenAge);
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
            Objects.requireNonNull(builderConsumer);
            MaxTokenAgeValidator.Builder builder = MaxTokenAgeValidator.builder();
            builderConsumer.accept(builder);
            claimValidators.add(builder.build());
            return this;
        }

        /**
         * Add new {@link AudienceValidator} with the expected audience.
         * This validator is mandatory by default.
         *
         * @param expectedAudience expected audience
         * @return updated builder instance
         */
        public Builder addAudienceValidator(String expectedAudience) {
            Objects.requireNonNull(expectedAudience);
            claimValidators.add(AudienceValidator.builder()
                                        .addExpectedAudience(expectedAudience)
                                        .build());
            return this;
        }

        /**
         * Add new {@link AudienceValidator} with the expected audience.
         * This validator is mandatory by default.
         *
         * @param expectedAudience expected audience
         * @return updated builder instance
         */
        public Builder addAudienceValidator(Set<String> expectedAudience) {
            Objects.requireNonNull(expectedAudience);
            claimValidators.add(AudienceValidator.builder()
                                        .expectedAudience(expectedAudience)
                                        .build());
            return this;
        }

        /**
         * Add new {@link AudienceValidator} instance based on the builder configuration.
         * This validator is mandatory by default.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder instance
         */
        public Builder addAudienceValidator(Consumer<AudienceValidator.Builder> builderConsumer) {
            Objects.requireNonNull(builderConsumer);
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

        List<ClaimValidator> claimValidators() {
            return claimValidators;
        }
    }
}
