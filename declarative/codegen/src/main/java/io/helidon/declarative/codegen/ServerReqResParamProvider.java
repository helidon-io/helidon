package io.helidon.declarative.codegen;

import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

class ServerReqResParamProvider implements io.helidon.declarative.codegen.spi.HttpParameterCodegenProvider {
    @Override
    public boolean codegen(List<Annotation> parameterAnnotations,
                           TypeName parameterType,
                           ClassModel.Builder classBuilder,
                           ContentBuilder<?> contentBuilder,
                           String serverRequestParamName,
                           String serverResponseParamName,
                           int methodIndex,
                           int paramIndex) {
        if (WebServerCodegenTypes.SERVER_REQUEST.equals(parameterType)) {
            contentBuilder.addContent(serverRequestParamName)
                    .addContent(";");
            return true;
        }
        if (WebServerCodegenTypes.SERVER_RESPONSE.equals(parameterType)) {
            contentBuilder.addContent(serverResponseParamName)
                    .addContent(";");
            return true;
        }
        return false;
    }
}
