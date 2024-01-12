package io.helidon.webserver.codegen;

import java.util.List;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

record ParamDef(TypeName type,
                String name,
                List<Annotation> qualifiers) {
}
