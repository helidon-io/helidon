package io.helidon.config.metadata.processor;

import io.helidon.common.types.TypeName;

class UsedTypes {
    /*
    Using config metadata
     */
    static TypeName META_CONFIGURED = TypeName.create("io.helidon.config.metadata.Configured");
    static TypeName META_OPTION = TypeName.create("io.helidon.config.metadata.ConfiguredOption");
    static TypeName META_OPTIONS = TypeName.create("io.helidon.config.metadata.ConfiguredOptions");

    /*
    Using builder API
     */
    static final TypeName BUILDER = TypeName.create("io.helidon.common.Builder");
    static final TypeName COMMON_CONFIG = TypeName.create("io.helidon.common.config.Config");
    static final TypeName CONFIG = TypeName.create("io.helidon.config.Config");
    static final TypeName BLUEPRINT = TypeName.create("io.helidon.builder.api.Prototype.Blueprint");
    static final TypeName CONFIGURED = TypeName.create("io.helidon.builder.api.Prototype.Configured");
    static final TypeName DESCRIPTION = TypeName.create("io.helidon.builder.api.Description");
    static final TypeName OPTION_SINGULAR = TypeName.create("io.helidon.builder.api.Option.Singular");
    static final TypeName OPTION_SAME_GENERIC = TypeName.create("io.helidon.builder.api.Option.SameGeneric");
    static final TypeName OPTION_CONFIGURED = TypeName.create("io.helidon.builder.api.Option.Configured");
    static final TypeName OPTION_REQUIRED = TypeName.create("io.helidon.builder.api.Option.Required");
    static final TypeName OPTION_PROVIDER = TypeName.create("io.helidon.builder.api.Option.Provider");
    static final TypeName OPTION_ALLOWED_VALUES = TypeName.create("io.helidon.builder.api.Option.AllowedValues");
    static final TypeName OPTION_ALLOWED_VALUE = TypeName.create("io.helidon.builder.api.Option.AllowedValue");
    static final TypeName OPTION_DEFAULT = TypeName.create("io.helidon.builder.api.Option.Default");
    static final TypeName OPTION_DEFAULT_INT = TypeName.create("io.helidon.builder.api.Option.DefaultInt");
    static final TypeName OPTION_DEFAULT_DOUBLE = TypeName.create("io.helidon.builder.api.Option.DefaultDouble");
    static final TypeName OPTION_DEFAULT_BOOLEAN = TypeName.create("io.helidon.builder.api.Option.DefaultBoolean");
    static final TypeName OPTION_DEFAULT_LONG = TypeName.create("io.helidon.builder.api.Option.DefaultLong");
    static final TypeName OPTION_DEFAULT_METHOD = TypeName.create("io.helidon.builder.api.Option.DefaultMethod");
    static final TypeName OPTION_DEFAULT_CODE = TypeName.create("io.helidon.builder.api.Option.DefaultCode");
}
