package io.helidon.validation;

import io.helidon.service.registry.Service;

/**
 * A validator service.
 * <p>
 * Validator allows programmatic validation of instances, properties and values.
 */
@Service.Contract
public interface Validator {
    /**
     * Validate an object that is annotated with {@link io.helidon.validation.Validation.Validated}.
     *
     * @param object object instance to validate
     * @return validator response
     */
    Validation.ValidatorResponse validate(Object object);

    /**
     * Validate a specific property of an object that is annotated with {@link io.helidon.validation.Validation.Validated}.
     * <p>
     * A property is considered to be one of the following:
     * <ul>
     *     <li>A record component with constraint annotation(s)</li>
     *     <li>A method with constraint annotation(s) that matches getter pattern - non-void return type, no parameters</li>
     *     <li>Non-private field with constraint annotation(s)</li>
     * </ul>
     *
     * @param object       object instance to validate
     * @param propertyName name of the property
     * @return validator response
     */
    Validation.ValidatorResponse validate(Object object,
                                          String propertyName);

    /**
     * Validate a value against a specific property of an object that is annotated with
     * {@link io.helidon.validation.Validation.Validated}.
     * <p>
     * A property is considered to be one of the following:
     * <ul>
     *     <li>A record component with constraint annotation(s)</li>
     *     <li>A method with constraint annotation(s) that matches getter pattern - non-void return type, no parameters</li>
     *     <li>Non-private field with constraint annotation(s)</li>
     * </ul>
     *
     * @param type         type annotated with {@link io.helidon.validation.Validation.Validated}
     * @param propertyName name of the property
     * @param value        value to check
     * @return validator response
     */
    Validation.ValidatorResponse validate(Class<?> type,
                                          String propertyName,
                                          Object value);
}
