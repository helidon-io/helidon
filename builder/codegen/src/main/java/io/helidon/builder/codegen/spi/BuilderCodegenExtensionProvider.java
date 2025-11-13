package io.helidon.builder.codegen.spi;

import io.helidon.common.types.TypeName;

public interface BuilderCodegenExtensionProvider {
    /**
     * Whether this extension provider supports the given type, that is configured by the user in
     * {@code io.helidon.builder.api.Prototype.Extension#value}.
     *
     * @return {@code true} if this provider supports the given type, {@code false} otherwise
     */
    boolean supports(TypeName type);

    /**
     * Create an extension for the given type(s), that was checked by {@link #supports(io.helidon.common.types.TypeName)}.
     *
     * @param supportedTypes types supported by this extension - at least one is always present
     * @return an extension to modify the generated code
     */
    BuilderCodegenExtension create(TypeName... supportedTypes);
}
