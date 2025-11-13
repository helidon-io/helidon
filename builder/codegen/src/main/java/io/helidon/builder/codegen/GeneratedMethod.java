package io.helidon.builder.codegen;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypedElementInfo;

/**
 * A method to be generated.
 * <p>
 * Rules for referenced static custom methods:
 * <ul>
 *     <li>The first parameter must be the Prototype type for custom prototype methods</li>
 *     <li>The first parameter must be the BuilderBase type for custom builder methods</li>
 *     <li>Custom factory methods are simply referenced</li>
 * </ul>
 */
public interface GeneratedMethod {
    /**
     * Definition of this method, including annotations.
     *
     * @return method definition
     */
    TypedElementInfo methodDefinition();

    /**
     * Update the method content.
     *
     * @param contentBuilder builder of content
     */
    void accept(ContentBuilder<?> contentBuilder);

    /**
     * Whether this method overrides an existing method from a super class/interface.
     *
     * @return whether this is an override
     */
    default boolean override() {
        return false;
    }
}
