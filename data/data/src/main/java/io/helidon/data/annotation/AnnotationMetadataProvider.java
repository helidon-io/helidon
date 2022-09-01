package io.helidon.data.annotation;

// HACK: Micronaout core class placeholder
public interface AnnotationMetadataProvider {

    default AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

}
