package io.helidon.declarative.codegen;

import java.util.List;
import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.spi.HttpParameterCodegenProvider;

import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_PATH_PARAM_ANNOTATION;

class HttpPathParamProvider extends AbstractParametersProvider implements HttpParameterCodegenProvider {
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
                .filter(it -> HTTP_PATH_PARAM_ANNOTATION.equals(it.typeName()))
                .findFirst();

        if (first.isEmpty()) {
            return false;
        }

        Annotation pathParam = first.get();
        String pathParamName = pathParam.value()
                .orElseThrow(() -> new CodegenException("@PathParam annotation must have a value."));

        contentBuilder.addContent(serverRequestParamName)
                .addContent(".path().pathParameters()");

        codegenFromParameters(contentBuilder, parameterType, pathParamName);

        return true;
    }
}
