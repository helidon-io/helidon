package io.helidon.webserver.codegen;

import io.helidon.common.types.TypeName;

class WebServerCodegenTypes {
    static final TypeName COMMON_CONTEXT = TypeName.create("io.helidon.common.context.Context");
    static final TypeName SERVER_REQUEST = TypeName.create("io.helidon.webserver.http.ServerRequest");
    static final TypeName SERVER_RESPONSE = TypeName.create("io.helidon.webserver.http.ServerResponse");
    static final TypeName HTTP_FEATURE = TypeName.create("io.helidon.webserver.http.HttpFeature");
    static final TypeName HTTP_METHOD = TypeName.create("io.helidon.http.Method");
    static final TypeName HTTP_STATUS = TypeName.create("io.helidon.http.Status");
    static final TypeName HTTP_PATH_ANNOTATION = TypeName.create("io.helidon.http.Http.Path");
    static final TypeName HTTP_METHOD_ANNOTATION = TypeName.create("io.helidon.http.Http.HttpMethod");
    static final TypeName HTTP_STATUS_ANNOTATION = TypeName.create("io.helidon.http.Http.Status");
    static final TypeName HTTP_ROUTING_BUILDER = TypeName.create("io.helidon.webserver.http.HttpRouting.Builder");
    static final TypeName HTTP_RULES = TypeName.create("io.helidon.webserver.http.HttpRules");
    static final TypeName SERVICE_CONTEXT = TypeName.create("io.helidon.common.context.Context__ServiceDescriptor");
    static final TypeName SERVICE_PROLOGUE = TypeName.create("io.helidon.http.Prologue__ServiceDescriptor");
    static final TypeName SERVICE_HEADERS = TypeName.create("io.helidon.http.Headers__ServiceDescriptor");
    static final TypeName SERVICE_SERVER_REQUEST = TypeName.create("io.helidon.webserver.ServerRequest__ServiceDescriptor");
    static final TypeName SERVICE_SERVER_RESPONSE = TypeName.create("io.helidon.webserver.ServerResponse__ServiceDescriptor");
    static final TypeName INJECT_SCOPE = TypeName.create("io.helidon.inject.Scope");
    static final TypeName INJECT_REQUEST_SCOPE_CTRL = TypeName.create("io.helidon.inject.RequestScopeControl");
}
