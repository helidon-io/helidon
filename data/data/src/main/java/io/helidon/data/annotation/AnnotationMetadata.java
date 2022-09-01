package io.helidon.data.annotation;

// HACK: Micronaout core class placeholder
public interface AnnotationMetadata {

    static final AnnotationMetadata EMPTY_METADATA = new AnnotationMetadata() {

    };

    /**
     * Checks whether this object has the given annotation on the object itself or inherited from a parent.
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasAnnotation(String annotation) {
        return false;
    }


}
