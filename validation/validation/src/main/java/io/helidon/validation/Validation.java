package io.helidon.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Validation annotations and related types.
 */
public final class Validation {
    private Validation() {
    }

    /**
     * Definition of a constraint.
     * A validator is discovered from the service registry - the first instance that has
     * {@link io.helidon.validation.Validation.ConstraintValidator} contract, and is named as the
     * fully qualified name of the constraint annotation.
     */
    @Documented
    @Target(ANNOTATION_TYPE)
    @Retention(CLASS)
    @Interception.Intercepted
    public @interface Constraint {
    }

    /**
     * This type will contain validations on getters (or record components) that cannot be intercepted.
     * Such a type will have a validator generated, that will be used from interceptors.
     * <p>
     * The generated type will be a TypeValidator named with the fully qualified class name of the
     * annotated type.
     */
    @Documented
    @Target(TYPE)
    public @interface Validated {
    }

    /**
     * Mark an element as validated even when no explicit constraints are added on it, to validate
     * the nested object structure.
     * <p>
     * Each object must be annotated with {@link io.helidon.validation.Validation.Validated}, as otherwise
     * we cannot know what to do (Helidon only supports build-time generated validations, we do not use
     * reflection to analyze types).
     */
    @Retention(CLASS)
    @Target({METHOD, FIELD, CONSTRUCTOR, PARAMETER, TYPE_USE})
    @Interception.Intercepted
    public @interface Valid {
        /**
         * Can be set to {@code false} to explicitly disable all validations on this element.
         *
         * @return whether to validate an element (deep validation)
         */
        boolean value() default true;
    }

    /**
     * Meta annotation marking a constraint as a parameters constraint.
     * This will require {@link io.helidon.validation.Validation.ConstraintValidatorParamsProvider}.
     */
    @Target(ANNOTATION_TYPE)
    public @interface ParamsConstraint {
    }

    public interface TypeValidator<T> {
        ValidatorResponse check(ConstraintValidatorContext context, T instance);
    }

    /**
     * Defines a constraint validation business logic.
     */
    public interface ConstraintValidatorProvider {
        ConstraintValidator create(TypeName typeName,
                                   Annotation constraintAnnotation);
    }

    /**
     * Defines a constraint validation business logic.
     */
    public interface ConstraintValidatorParamsProvider {
        ConstraintValidatorParams create(Annotation constraintAnnotation);
    }

    public interface ConstraintValidator {
        ValidatorResponse check(ConstraintValidatorContext context, Object value);
    }

    public interface ConstraintValidatorParams {
        ValidatorResponse checkMethodParams(ConstraintValidatorContext context,
                                            TypedElementInfo method,
                                            Object currentInstance,
                                            Object[] parameterValues);

        ValidatorResponse checkMethodParamsReturn(ConstraintValidatorContext context,
                                                  TypedElementInfo method,
                                                  Object currentInstance,
                                                  Object returnValue);

        ValidatorResponse checkConstructorParams(ConstraintValidatorContext context,
                                                 TypedElementInfo method,
                                                 Object[] parameterValues);

        ValidatorResponse checkConstructorReturn(ConstraintValidatorContext context,
                                                 TypedElementInfo method,
                                                 Object returnValue);
    }

    public interface ValidatorResponse {
        boolean failed();

        String message();

        List<ConstraintViolation> violations();

        ValidatorResponse merge(ValidatorResponse other);

        ValidationException toException();
    }
}
