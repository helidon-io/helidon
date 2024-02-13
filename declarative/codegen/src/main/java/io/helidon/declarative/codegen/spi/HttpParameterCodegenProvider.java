package io.helidon.declarative.codegen.spi;

import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

public interface HttpParameterCodegenProvider {
    /**
     * Code generate parameter assignment.
     * The content builder's current content will be something like {@code var someParam =}, and
     * this method is responsible for adding the appropriate extraction of parameter from
     * server request, server response, or other component.
     *
     * @param classBuilder
     * @param contentBuilder
     * @param serverRequestParamName
     * @param serverResponseParamName
     * @return whether code was generated
     */
    boolean codegen(List<Annotation> parameterAnnotations,
                    TypeName parameterType,
                    ClassModel.Builder classBuilder,
                    ContentBuilder<?> contentBuilder,
                    String serverRequestParamName,
                    String serverResponseParamName,
                    int methodIndex,
                    int paramIndex);
}
