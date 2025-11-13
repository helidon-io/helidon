package io.helidon.builder.codegen;

import java.util.List;

import io.helidon.common.types.TypeName;

/**
 * Some static methods on custom methods (and deprecated option on the blueprint itself)
 * may be annotated with {@code Prototype.FactoryMethod}.
 * <p>
 * Such methods can be used to map from configuration to a type.
 */
public interface FactoryMethod {
    /**
     * Type declaring the factory method.
     *
     * @return type declaring the factory method
     */
    TypeName declaringType();

    /**
     * Return type of the factory method.
     *
     * @return return type of the factory method
     */
    TypeName returnType();

    /**
     * Name of the factory method.
     *
     * @return factory method name
     */
    String methodName();

    /**
     * Parameters of the factory method.
     *
     * @return parmmeters
     */
    List<Parameter> parameters();

    /**
     * A parameter definition.
     */
    interface Parameter {
        /**
         * Name of the parameter.
         *
         * @return parameter name
         */
        String name();

        /**
         * Type of the parameter.
         *
         * @return parameter type
         */
        TypeName type();
    }
}
