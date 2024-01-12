package io.helidon.webserver.codegen;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.inject.codegen.InjectCodegenTypes;

import static io.helidon.webserver.codegen.WebServerCodegenExtension.GENERATOR;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.COMMON_CONTEXT;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.HTTP_METHOD;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.HTTP_ROUTING_BUILDER;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.HTTP_RULES;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.INJECT_REQUEST_SCOPE_CTRL;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.INJECT_SCOPE;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVER_REQUEST;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVER_RESPONSE;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVICE_CONTEXT;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVICE_HEADERS;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVICE_PROLOGUE;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVICE_SERVER_REQUEST;
import static io.helidon.webserver.codegen.WebServerCodegenTypes.SERVICE_SERVER_RESPONSE;

class GenerateEndpointService {
    static void generate(CodegenContext ctx, TypeInfo endpoint, String classNameBase, String path, List<MethodDef> httpMethods) {
        String endpointServiceName = classNameBase + "__Service";

        TypeName generatedType = TypeName.builder()
                .packageName(endpoint.typeName().packageName())
                .className(endpointServiceName)
                .build();

        ClassModel.Builder endpointService = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 endpoint.typeName(),
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               endpoint.typeName(),
                                                               generatedType,
                                                               "1",
                                                               ""))
                .type(generatedType)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(InjectCodegenTypes.INJECTION_SINGLETON))
                .addInterface(WebServerCodegenTypes.HTTP_FEATURE);

        // request scope control
        // each method's generated service
        addFields(endpointService, httpMethods);
        // constructor injecting all the fields
        addConstructor(endpointService, httpMethods);
        // HttpFeature.setup(HttpRouting.Builder routing)
        addSetupMethod(endpointService, path);
        // private void routing(HttpRules rules)
        addRoutingMethod(endpointService, httpMethods);

        // now each method
        for (MethodDef httpMethod : httpMethods) {
            addEndpointMethod(endpointService, httpMethod);
        }

        // private Scope startRequestScope(ServerRequest req, ServerResponse res)
        addStartRequestScopeMethod(endpointService);

        ctx.filer()
                .writeSourceFile(endpointService.build(), endpoint.originatingElement().orElseGet(endpoint::typeName));
    }

    private static void addConstructor(ClassModel.Builder endpointService, List<MethodDef> httpMethods) {
        endpointService.addConstructor(ctr -> ctr
                .addAnnotation(Annotation.create(InjectCodegenTypes.INJECTION_INJECT))
                .addParameter(param -> param
                        .type(INJECT_REQUEST_SCOPE_CTRL)
                        .name("requestCtrl"))
                .addContentLine("this.requestCtrl = requestCtrl;")
                .update(it -> {
                    for (MethodDef httpMethod : httpMethods) {
                        String serviceField = httpMethod.serviceField();
                        it.addParameter(param -> param
                                .type(httpMethod.supplierType())
                                .name(serviceField));
                        it.addContent("this.")
                                .addContent(serviceField)
                                .addContent(" = ")
                                .addContent(serviceField)
                                .addContentLine(";");

                    }
                })
        );
    }

    private static void addFields(ClassModel.Builder endpointService, List<MethodDef> httpMethods) {
        endpointService.addField(requestCtrl -> requestCtrl
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(INJECT_REQUEST_SCOPE_CTRL)
                .name("requestCtrl")
        );

        for (MethodDef httpMethod : httpMethods) {
            endpointService.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(httpMethod.supplierType())
                    .name(httpMethod.serviceField())
            );
        }
    }

    private static void addEndpointMethod(ClassModel.Builder endpointService, MethodDef httpMethod) {
        endpointService.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .name(httpMethod.serviceMethod())
                .addParameter(req -> req
                        .type(SERVER_REQUEST)
                        .name("req"))
                .addParameter(res -> res
                        .type(SERVER_RESPONSE)
                        .name("res"))
                .update(it -> httpMethod.methodElement().throwsChecked()
                        .forEach(checked -> it.addThrows(thrown -> thrown.type(checked))))
                .addContent("try (")
                .addContent(INJECT_SCOPE)
                .addContentLine(" ignored = startRequestScope(req, res)) {")
                .addContent(httpMethod.serviceField())
                .addContentLine(".get()")
                .increaseContentPadding()
                .addContentLine(".invoke(res);")
                .decreaseContentPadding()
                .addContentLine("}")
        );
    }

    private static void addRoutingMethod(ClassModel.Builder endpointService, List<MethodDef> httpMethods) {
        // we must add each constant just once, this is to keep track of them
        Set<String> addedHttpMethods = new HashSet<>();
        endpointService.addMethod(routing -> routing
                .accessModifier(AccessModifier.PRIVATE)
                .name("routing")
                .addParameter(rules -> rules
                        .type(HTTP_RULES)
                        .name("rules"))
                .update(it -> httpMethods.forEach(methodDef -> addRoutingMethodContent(endpointService,
                                                                                       it,
                                                                                       methodDef,
                                                                                       addedHttpMethods)))
        );
    }

    private static void addRoutingMethodContent(ClassModel.Builder endpointService,
                                                Method.Builder routing,
                                                MethodDef methodDef,
                                                Set<String> addedHttpMethods) {

        routing.addContent("rules.");

        String httpMethod = methodDef.httpMethod();
        switch (httpMethod) {
        case "GET", "POST", "PUT", "DELETE", "TRACE", "HEAD":
            routing.addContent(httpMethod.toLowerCase(Locale.ROOT))
                    .addContent("(");
            break;
        default:
            if (addedHttpMethods.add(httpMethod)) {
                endpointService.addField(httpMethodField -> httpMethodField
                        .accessModifier(AccessModifier.PRIVATE)
                        .isStatic(true)
                        .isFinal(true)
                        .type(HTTP_METHOD)
                        .name("ENDPOINT_METHOD_" + httpMethod)
                        .addContent(HTTP_METHOD)
                        .addContent(".create(\"")
                        .addContent(httpMethod)
                        .addContent("\"")
                );
            }
            routing.addContent("route(ENDPOINT_METHOD_" + httpMethod + ", ");
            break;
        }

        if (methodDef.path().isPresent()) {
            routing.addContent("\"")
                    .addContent(methodDef.path().get())
                    .addContent("\", ");
        }

        routing.addContent("this::")
                .addContent(methodDef.serviceMethod())
                .addContentLine(");");
    }

    private static void addSetupMethod(ClassModel.Builder endpointService, String path) {
        endpointService.addMethod(setup -> setup
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .name("setup")
                .addParameter(routing -> routing
                        .name("routing")
                        .type(HTTP_ROUTING_BUILDER))
                .addContent("routing.register(\"")
                .addContent(path)
                .addContentLine("\", this::routing);"));
    }

    private static void addStartRequestScopeMethod(ClassModel.Builder endpointService) {
        // this method is always the same in all generated endpoints, we can investigate if there is a good place
        // to put it as a shared method
        endpointService.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(INJECT_SCOPE)
                .name("startRequestScope")
                .addParameter(req -> req
                        .name("req")
                        .type(SERVER_REQUEST))
                .addParameter(req -> req
                        .name("res")
                        .type(SERVER_RESPONSE))
                .addContentLine("return requestCtrl.startRequestScope(\"http_\" + req.id(),")
                .increaseContentPadding()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(TypeNames.MAP)
                .addContentLine(".of(")
                .increaseContentPadding()
                .addContent(SERVICE_CONTEXT)
                .addContent(".INSTANCE, (")
                .addContent(TypeNames.SUPPLIER)
                .addContent("<")
                .addContent(COMMON_CONTEXT)
                .addContentLine(">) req::context,")
                .update(it -> addInitialBinding(it, SERVICE_PROLOGUE, "req.prologue(),"))
                .update(it -> addInitialBinding(it, SERVICE_HEADERS, "req.headers(),"))
                .update(it -> addInitialBinding(it, SERVICE_SERVER_REQUEST, "req,"))
                .update(it -> addInitialBinding(it, SERVICE_SERVER_RESPONSE, "res"))
                .decreaseContentPadding()
                .addContentLine("));")
        );
    }

    private static void addInitialBinding(Method.Builder method, TypeName serviceTypeName, String content) {
        method.addContent(serviceTypeName)
                .addContent(".INSTANCE, ")
                .addContentLine(content);
    }
}
