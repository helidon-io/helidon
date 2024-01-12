package io.helidon.webserver.codegen;

import java.util.List;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * @param methodElement the method element as discovered
 * @param path          path of the method (if declared)
 * @param className     name of the generated class of the method invocation
 * @param serviceField  name of the field that will host the method supplier in endpoint generated class
 * @param serviceMethod name of the method that will host the method registered with routing
 * @param methodName    name of the method as declared on the endpoint
 * @param httpMethod    name of the HTTP method (all upper case)
 * @param params        all parameters that must be injected
 */
record MethodDef(TypedElementInfo methodElement,
                 Optional<String> path, // "/greet"
                 String className, // GreetEndpoint_greetNamed__Service
                 String serviceField, // endpoint_greetNamed_service
                 String serviceMethod, // endpoint_greetNamed
                 String methodName, // greetNamed
                 String httpMethod, // GET
                 List<ParamDef> params

) {
    TypeName supplierType() {
        return TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(TypeName.create(className))
                .build();
    }
}
