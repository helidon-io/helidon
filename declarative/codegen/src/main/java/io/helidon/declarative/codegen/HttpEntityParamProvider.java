package io.helidon.declarative.codegen;

import java.util.List;
import java.util.Optional;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.spi.HttpParameterCodegenProvider;

import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_ENTITY_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_HEADER_PARAM_ANNOTATION;

class HttpEntityParamProvider extends AbstractParametersProvider implements HttpParameterCodegenProvider {
    @Override
    public boolean codegen(List<Annotation> parameterAnnotations,
                           TypeName parameterType,
                           ClassModel.Builder classBuilder,
                           ContentBuilder<?> contentBuilder,
                           String serverRequestParamName,
                           String serverResponseParamName,
                           int methodIndex,
                           int paramIndex) {

        Optional<Annotation> first = parameterAnnotations.stream()
                .filter(it -> HTTP_ENTITY_PARAM_ANNOTATION.equals(it.typeName()))
                .findFirst();

        if (first.isEmpty()) {
            return false;
        }

        contentBuilder.addContent(serverRequestParamName)
                .addContent(".content().as(")
                .addContent(parameterType)
                .addContent(".class);");

        return true;
    }
}
