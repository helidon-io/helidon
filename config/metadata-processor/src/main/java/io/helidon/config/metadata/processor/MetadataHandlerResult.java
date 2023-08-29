package io.helidon.config.metadata.processor;

import io.helidon.common.types.TypeName;

/**
 * Result of annotation processing.
 *
 * @param targetType     type that is configured (result of the builder, runtime type of a prototype)
 * @param moduleName     module of the type
 * @param configuredType collected configuration metadata
 */
record MetadataHandlerResult(TypeName targetType,
                             String moduleName,
                             ConfiguredType configuredType) {
}
