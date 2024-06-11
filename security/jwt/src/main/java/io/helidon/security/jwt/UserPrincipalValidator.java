package io.helidon.security.jwt;

import java.util.List;

import io.helidon.common.Errors;

/**
 * User principal validator.
 */
public final class UserPrincipalValidator extends OptionalValidator {

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
