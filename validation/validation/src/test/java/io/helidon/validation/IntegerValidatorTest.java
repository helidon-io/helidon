package io.helidon.validation;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class IntegerValidatorTest {
    private final Validation.ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;

    IntegerValidatorTest() {
        this.validatorProvider = Services.getNamed(Validation.ConstraintValidatorProvider.class,
                                                   "io.helidon.validation.Constraints.Integer.Min");
        this.ctx = new ConstraintValidatorContextImpl(IntegerValidatorTest.class, this);

    }

    @Test
    public void testByteValidation() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BYTE, Annotation.create(Constraints.Integer.Min.class,
                                                                                             "18"));

        var response = validator.check(ctx, (byte) 19);

        assertThat(response.failed(), is(false));

        response = validator.check(ctx, (byte) 17);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("0x11 is less than 0x12"));
    }

    @Test
    public void testIntValidation() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.create(Constraints.Integer.Min.class,
                                                                                            "18"));

        var response = validator.check(ctx, 19);

        assertThat(response.failed(), is(false));

        response = validator.check(ctx, 17);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("17 is less than 18"));
    }

    @Test
    public void testShortValidation() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_SHORT, Annotation.create(Constraints.Integer.Min.class,
                                                                                              "18"));

        var response = validator.check(ctx, (short) 19);

        assertThat(response.failed(), is(false));

        response = validator.check(ctx, (short) 17);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("17 is less than 18"));
    }

    @Test
    public void testCharValidation() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_CHAR,
                                                 Annotation.create(Constraints.Integer.Min.class,
                                                                   "38"));

        var response = validator.check(ctx, (char) 39);

        assertThat(response.failed(), is(false));

        response = validator.check(ctx, (char) 37);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("'%' (37) is less than '&' (38)"));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Constraints.Integer.Min.class))
                .putValue("value", 38)
                .putValue("message", "No parameter message")
                .build());

        var response = validator.check(ctx, 39);

        assertThat(response.failed(), is(false));

        response = validator.check(ctx, 37);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("No parameter message"));
    }
}
