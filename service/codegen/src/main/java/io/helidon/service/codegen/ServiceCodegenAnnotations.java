package io.helidon.service.codegen;

import io.helidon.common.types.Annotation;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;

class ServiceCodegenAnnotations {
    static final Annotation WILDCARD_NAMED = Annotation.create(SERVICE_ANNOTATION_NAMED, "*");
}
