/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.openapi;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import io.smallrye.openapi.api.models.ComponentsImpl;
import io.smallrye.openapi.api.models.ExternalDocumentationImpl;
import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.api.models.OperationImpl;
import io.smallrye.openapi.api.models.PathItemImpl;
import io.smallrye.openapi.api.models.PathsImpl;
import io.smallrye.openapi.api.models.callbacks.CallbackImpl;
import io.smallrye.openapi.api.models.examples.ExampleImpl;
import io.smallrye.openapi.api.models.headers.HeaderImpl;
import io.smallrye.openapi.api.models.info.ContactImpl;
import io.smallrye.openapi.api.models.info.InfoImpl;
import io.smallrye.openapi.api.models.info.LicenseImpl;
import io.smallrye.openapi.api.models.links.LinkImpl;
import io.smallrye.openapi.api.models.media.ContentImpl;
import io.smallrye.openapi.api.models.media.DiscriminatorImpl;
import io.smallrye.openapi.api.models.media.EncodingImpl;
import io.smallrye.openapi.api.models.media.MediaTypeImpl;
import io.smallrye.openapi.api.models.media.SchemaImpl;
import io.smallrye.openapi.api.models.media.XMLImpl;
import io.smallrye.openapi.api.models.parameters.ParameterImpl;
import io.smallrye.openapi.api.models.parameters.RequestBodyImpl;
import io.smallrye.openapi.api.models.responses.APIResponseImpl;
import io.smallrye.openapi.api.models.responses.APIResponsesImpl;
import io.smallrye.openapi.api.models.security.OAuthFlowImpl;
import io.smallrye.openapi.api.models.security.OAuthFlowsImpl;
import io.smallrye.openapi.api.models.security.ScopesImpl;
import io.smallrye.openapi.api.models.security.SecurityRequirementImpl;
import io.smallrye.openapi.api.models.security.SecuritySchemeImpl;
import io.smallrye.openapi.api.models.servers.ServerImpl;
import io.smallrye.openapi.api.models.servers.ServerVariableImpl;
import io.smallrye.openapi.api.models.servers.ServerVariablesImpl;
import io.smallrye.openapi.api.models.tags.TagImpl;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.ExternalDocumentation;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.callbacks.Callback;
import org.eclipse.microprofile.openapi.models.examples.Example;
import org.eclipse.microprofile.openapi.models.headers.Header;
import org.eclipse.microprofile.openapi.models.info.Contact;
import org.eclipse.microprofile.openapi.models.info.Info;
import org.eclipse.microprofile.openapi.models.info.License;
import org.eclipse.microprofile.openapi.models.links.Link;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.Discriminator;
import org.eclipse.microprofile.openapi.models.media.Encoding;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.media.XML;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.OAuthFlow;
import org.eclipse.microprofile.openapi.models.security.OAuthFlows;
import org.eclipse.microprofile.openapi.models.security.Scopes;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;
import org.eclipse.microprofile.openapi.models.servers.Server;
import org.eclipse.microprofile.openapi.models.servers.ServerVariable;
import org.eclipse.microprofile.openapi.models.servers.ServerVariables;
import org.eclipse.microprofile.openapi.models.tags.Tag;
import org.yaml.snakeyaml.TypeDescription;

class SnakeYAMLParserHelper<T extends TypeDescription> {

    private final Map<Class<?>, T> types = new HashMap<>();

    static <T extends TypeDescription> SnakeYAMLParserHelper<T> create(
            BiFunction<Class<?>, Class<?>, T> factoryFunction) {
        return new SnakeYAMLParserHelper<T>(factoryFunction);
    }

    Map<Class<?>, T> types() {
        return types;
    }

    Set<Map.Entry<Class<?>, T>> entrySet() {
        return types.entrySet();
    }

    Set<Class<?>> keySet() {
        return types.keySet();
    }

    boolean containsKey(Class<?> type) {
        return types.containsKey(type);
    }

    T get(Class<?> clazz) {
        return types.get(clazz);
    }

    private SnakeYAMLParserHelper(BiFunction<Class<?>, Class<?>, T> factoryFunction) {
        T discriminator = add(factoryFunction, Discriminator.class, DiscriminatorImpl.class);
        discriminator.addPropertyParameters("mapping", String.class, String.class);

        T components = add(factoryFunction, Components.class, ComponentsImpl.class);
        components.addPropertyParameters("schemas", String.class, Schema.class);
        components.addPropertyParameters("responses", String.class, APIResponse.class);
        components.addPropertyParameters("parameters", String.class, Parameter.class);
        components.addPropertyParameters("examples", String.class, Example.class);
        components.addPropertyParameters("requestBodies", String.class, RequestBody.class);
        components.addPropertyParameters("headers", String.class, Header.class);
        components.addPropertyParameters("securitySchemes", String.class, SecurityScheme.class);
        components.addPropertyParameters("links", String.class, Link.class);
        components.addPropertyParameters("callbacks", String.class, Callback.class);

        add(factoryFunction, Info.class, InfoImpl.class);

        add(factoryFunction, Example.class, ExampleImpl.class);

        T securityRequirement = add(factoryFunction, SecurityRequirement.class, SecurityRequirementImpl.class);
        securityRequirement.addPropertyParameters("schemes", String.class);

        T apiResponse = add(factoryFunction, APIResponse.class, APIResponseImpl.class);
        apiResponse.addPropertyParameters("headers", String.class, Header.class);
        apiResponse.addPropertyParameters("links", String.class, Link.class);

        T paths = add(factoryFunction, Paths.class, PathsImpl.class);
        paths.addPropertyParameters("pathItems", String.class, PathItem.class);

        T mediaType = add(factoryFunction, MediaType.class, MediaTypeImpl.class);
        mediaType.addPropertyParameters("examples", String.class, Example.class);
        mediaType.addPropertyParameters("encoding", String.class, Encoding.class);

        add(factoryFunction, OAuthFlow.class, OAuthFlowImpl.class);

        add(factoryFunction, License.class, LicenseImpl.class);

        T encoding = add(factoryFunction, Encoding.class, EncodingImpl.class);
        encoding.addPropertyParameters("headers", String.class, Header.class);

        T serverVariable = add(factoryFunction, ServerVariable.class, ServerVariableImpl.class);
        serverVariable.addPropertyParameters("enumeration", String.class);

        T pathItem = add(factoryFunction, PathItem.class, PathItemImpl.class);
        pathItem.addPropertyParameters("servers", Server.class);
        pathItem.addPropertyParameters("parameters", Parameter.class);

        add(factoryFunction, Tag.class, TagImpl.class);

        T operation = add(factoryFunction, Operation.class, OperationImpl.class);
        operation.addPropertyParameters("tags", String.class);
        operation.addPropertyParameters("parameters", Parameter.class);
        operation.addPropertyParameters("callbacks", String.class, Callback.class);
        operation.addPropertyParameters("security", SecurityRequirement.class);
        operation.addPropertyParameters("servers", Server.class);

        T schema = add(factoryFunction, Schema.class, SchemaImpl.class);
        schema.addPropertyParameters("enumeration", Object.class);
        schema.addPropertyParameters("required", String.class);
        schema.addPropertyParameters("properties", String.class, Schema.class);
        schema.addPropertyParameters("allOf", Schema.class);
        schema.addPropertyParameters("anyOf", Schema.class);
        schema.addPropertyParameters("oneOf", Schema.class);

        T header = add(factoryFunction, Header.class, HeaderImpl.class);
        header.addPropertyParameters("examples", String.class, Example.class);

        T callback = add(factoryFunction, Callback.class, CallbackImpl.class);
        callback.addPropertyParameters("pathItems", String.class, PathItem.class);

        add(factoryFunction, RequestBody.class, RequestBodyImpl.class);

        add(factoryFunction, SecurityScheme.class, SecuritySchemeImpl.class);

        add(factoryFunction, Contact.class, ContactImpl.class);

        T content = add(factoryFunction, Content.class, ContentImpl.class);
        content.addPropertyParameters("mediaTypes", String.class, MediaType.class);

        add(factoryFunction, OAuthFlows.class, OAuthFlowsImpl.class);

        T serverVariables = add(factoryFunction, ServerVariables.class, ServerVariablesImpl.class);
        serverVariables.addPropertyParameters("serverVariables", String.class, ServerVariable.class);

        T link = add(factoryFunction, Link.class, LinkImpl.class);
        link.addPropertyParameters("parameters", String.class, Object.class);

        T parameter = add(factoryFunction, Parameter.class, ParameterImpl.class);
        parameter.addPropertyParameters("examples", String.class, Example.class);

        T scopes = add(factoryFunction, Scopes.class, ScopesImpl.class);
        scopes.addPropertyParameters("scopes", String.class, String.class);

        T openApi = add(factoryFunction, OpenAPI.class, OpenAPIImpl.class);
        openApi.addPropertyParameters("servers", Server.class);
        openApi.addPropertyParameters("security", SecurityRequirement.class);
        openApi.addPropertyParameters("tags", Tag.class);

        T apiResponses = add(factoryFunction, APIResponses.class, APIResponsesImpl.class);
        apiResponses.addPropertyParameters("aPIResponses", String.class, APIResponse.class);

        add(factoryFunction, ExternalDocumentation.class, ExternalDocumentationImpl.class);

        T server = add(factoryFunction, Server.class, ServerImpl.class);
        server.addPropertyParameters("variables", String.class, ServerVariable.class);

        add(factoryFunction, XML.class, XMLImpl.class);
    }

    private T add(BiFunction<Class<?>, Class<?>, T> factoryFunction, Class<?> type, Class<?> implementation) {
        T typeDescription = factoryFunction.apply(type, implementation);
        types.put(typeDescription.getType(), typeDescription);
        return typeDescription;
    }
}
