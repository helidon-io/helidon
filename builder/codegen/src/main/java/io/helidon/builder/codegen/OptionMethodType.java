/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

/**
 * All possible setters to be generated.
 */
public enum OptionMethodType {
    /**
     * Getter added to the prototype interface. This method may not be generated if the method is inherited from a public
     * interface.
     * <p>
     * All methods from blueprint are re-generated on prototype.
     */
    PROTOTYPE_GETTER,
    /**
     * Getter on the prototype implementation class. This method always returns the declared type, as it implements a
     * method either from the prototype or from an inherited public interface.
     */
    IMPL_GETTER,
    /**
     * Getter on the builder base class. Returns the declared type except for required options without a default,
     * where it returns {@code Optional} of the declared type.
     */
    BUILDER_GETTER,
    /**
     * The "main" setter method on the builder base class.
     * This method uses the declared type of the option, except for {@link java.util.Optional} and
     * {@link java.util.function.Supplier} options, where it uses the first type argument, and for char array, where we
     * use String.
     */
    BUILDER_SETTER,
    /**
     * Setter specific to char array options.
     * There will always be the {@link #BUILDER_SETTER} with {@link java.lang.String} parameter, and under this type
     * a setter with char array parameter.
     */
    BUILDER_SETTER_CHAR_ARRAY,
    /**
     * Only created for {@link java.util.Optional} and {@link java.util.function.Supplier} options, with the
     * declared type.
     */
    BUILDER_SETTER_DECLARED,
    /**
     * Only created for {@link java.util.Set}, {@link java.util.List} options. Uses the declared type, but instead of
     * replacing the values as {@link #BUILDER_SETTER} does, this method adds the values to the collection.
     */
    BUILDER_ADD_COLLECTION,
    /**
     * Method to clear the current value(s) of the option.
     * Only generated for {@link java.util.Set}, {@link java.util.List}, {@link java.util.Map}, and {@link java.util.Optional}
     * options.
     */
    BUILDER_CLEAR,
    /**
     * Singular add/put method for {@link java.util.List}, {@link java.util.Set}, or {@link java.util.Map}.
     * Must be explicitly annotated on an option, otherwise singular methods are not generated.
     *
     * @see io.helidon.builder.codegen.OptionInfo#singular()
     */
    BUILDER_SINGULAR_ADD,
    /**
     * Singular add method for builder consumer.
     *
     * @see io.helidon.builder.codegen.OptionInfo#singular()
     * @see io.helidon.builder.codegen.OptionInfo#builderInfo()
     */
    BUILDER_SINGULAR_ADD_CONSUMER,
    /**
     * Singular add method to add a value to a collection that is a value of a {@link java.util.Map}.
     *
     * @see io.helidon.builder.codegen.OptionInfo#singular()
     */
    BUILDER_SINGULAR_ADD_TO_MAP_VALUE,
    /**
     * Singular add method to add values to a collection that is a value of a {@link java.util.Map}.
     *
     * @see io.helidon.builder.codegen.OptionInfo#singular()
     */
    BUILDER_SINGULAR_ADD_TO_MAP_VALUES,
    /**
     * Only created if the option type itself has a builder. This method uses a {@link java.util.function.Consumer}
     * of the builder type.
     *
     * @see io.helidon.builder.codegen.OptionInfo#builderInfo()
     */
    BUILDER_SETTER_CONSUMER,
    /**
     * Only created if the option type is built by another prototype, or if there is a factory method to build
     * the type from another prototype.
     */
    BUILDER_SETTER_RUNTIME_TYPE_PROTOTYPE,
    /**
     * Only created if the option type itself has a builder. This method uses a {@link java.util.function.Supplier}
     * of the type, as our builders are also {@link java.util.function.Supplier supplier}, so you can pass an instance
     * of the builder.
     *
     * @see io.helidon.builder.codegen.OptionInfo#builderInfo()
     */
    BUILDER_SETTER_SUPPLIER
}
