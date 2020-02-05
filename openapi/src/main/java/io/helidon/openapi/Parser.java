/*
 * Copyright (c) 2019-2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.openapi;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Parses an OpenAPI static file into the corresponding OpenAPI model.
 * This implementation uses SnakeYAML for parsing and the SmallRye MP OpenAPI interfaces and
 * classes for the internal model.
 */
class Parser {

    private Parser() {}

    static OpenAPI parse(InputStream inputStream, OpenAPISupport.OpenAPIMediaType mediaType) {
        return mediaType.equals(OpenAPISupport.OpenAPIMediaType.JSON) ? parseJSON(inputStream) : parseYAML(inputStream);
    }
    static OpenAPI parseJSON(InputStream inputStream) {
        return parseYAML(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    static OpenAPI parseYAML(InputStream inputStream) {
        return parseYAML(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
    static OpenAPI parseYAML(Reader input) {
        TypeDescription openAPITD = ExpandedTypeDescription.create(OpenAPI.class, OpenAPIImpl.class);
        openAPITD.addPropertyParameters("security", SecurityRequirement.class);
        openAPITD.addPropertyParameters("servers", Server.class);
        openAPITD.addPropertyParameters("tags", Tag.class);

        Constructor topConstructor = new CustomConstructor(openAPITD);

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(Info.class, InfoImpl.class));
        topConstructor.addTypeDescription(ExpandedTypeDescription.create(Contact.class, ContactImpl.class));
        topConstructor.addTypeDescription(ExpandedTypeDescription.create(License .class, LicenseImpl .class));
        topConstructor.addTypeDescription(new TypeDescription(Content .class, ContentImpl .class));
        topConstructor.addTypeDescription(new TypeDescription(ExternalDocumentation.class, ExternalDocumentationImpl.class));

        TypeDescription serverTD = ExpandedTypeDescription.create(Server.class, ServerImpl.class);
        topConstructor.addTypeDescription(serverTD);

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(ServerVariables.class, ServerVariablesImpl .class));

        TypeDescription serverVariableTD = ExpandedTypeDescription.create(ServerVariable.class, ServerVariableImpl.class);
        serverVariableTD.addPropertyParameters("enumeration", String.class);
        topConstructor.addTypeDescription(serverVariableTD);

        TypeDescription securityRequirementTD = new TypeDescription(SecurityRequirement.class, SecurityRequirementImpl.class);
        securityRequirementTD.addPropertyParameters("scheme", String.class);
        securityRequirementTD.addPropertyParameters("schemes", String.class, String.class);
        topConstructor.addTypeDescription(securityRequirementTD);

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(Tag.class, TagImpl.class));

        TypeDescription pathsTD = ExpandedTypeDescription.create(Paths.class, PathsImpl.class);
        pathsTD.addPropertyParameters("pathItems", String.class, PathItem.class);
        topConstructor.addTypeDescription(pathsTD);

        TypeDescription pathItemTD = ExpandedTypeDescription.create(PathItem.class, PathItemImpl.class)
                .addRef();
        // The Operation method names have upper-case HTTP method names (e.g., getPUT) but the
        // yaml property names are lower-case (e.g., 'put').
        for (PathItem.HttpMethod m : PathItem.HttpMethod.values()) {
            pathItemTD.substituteProperty(m.name().toLowerCase(), Operation.class, getter(m), setter(m));
        }
        pathItemTD.addPropertyParameters("servers", Server.class);
        pathItemTD.addPropertyParameters("parameters", Parameter.class);
        topConstructor.addTypeDescription(pathItemTD);

        TypeDescription operationTD = ExpandedTypeDescription.create(Operation.class, OperationImpl.class);
        operationTD.addPropertyParameters("callbacks", String.class, Callback.class);
        operationTD.addPropertyParameters("security", SecurityRequirement.class);
        operationTD.addPropertyParameters("servers", Server.class);
        operationTD.addPropertyParameters("tags", String.class);
        topConstructor.addTypeDescription(operationTD);

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(APIResponses.class, APIResponsesImpl .class));

        TypeDescription componentsTD = new TypeDescription(Components.class, ComponentsImpl.class);
        componentsTD.addPropertyParameters("schemas", String.class, Schema.class);
        componentsTD.addPropertyParameters("responses", String.class, APIResponse.class);
        componentsTD.addPropertyParameters("parameters", String.class, Parameter.class);
        componentsTD.addPropertyParameters("examples", String.class, Example.class);
        componentsTD.addPropertyParameters("requestBodies", String.class, RequestBody.class);
        componentsTD.addPropertyParameters("headers", String.class, Header.class);
        componentsTD.addPropertyParameters("securitySchemes", String.class, SecurityScheme.class);
        componentsTD.addPropertyParameters("links", String.class, Link.class);
        componentsTD.addPropertyParameters("callbacks", String.class, Callback.class);
        topConstructor.addTypeDescription(componentsTD);

        TypeDescription schemaTD = ExpandedTypeDescription.SchemaTypeDescription.create(Schema.class, SchemaImpl.class)
                .addEnum("type", Schema.SchemaType::valueOf)
                .addRef();
        schemaTD.addPropertyParameters("properties", String.class, Schema.class);
        schemaTD.addPropertyParameters("required", String.class);
        schemaTD.addPropertyParameters("allOf", Schema.class);
        schemaTD.addPropertyParameters("anyOf", Schema.class);
        schemaTD.addPropertyParameters("oneOf", Schema.class);
        schemaTD.substituteProperty("enum", List.class, "getEnumeration", "setEnumeration");
        schemaTD.substituteProperty("default", Object.class, "getDefaultValue", "setDefaultValue");

        topConstructor.addTypeDescription(schemaTD);

        TypeDescription apiResponseTD = ExpandedTypeDescription.create(APIResponse.class, APIResponseImpl.class)
                .addRef();
        apiResponseTD.addPropertyParameters("headers", String.class, Header.class);
        apiResponseTD.addPropertyParameters("links", String.class, Link.class);
        topConstructor.addTypeDescription(apiResponseTD);

        TypeDescription parameterTD = ExpandedTypeDescription.create(Parameter.class, ParameterImpl.class)
                .addEnum("in", Parameter.In::valueOf)
                .addRef();
        parameterTD.addPropertyParameters("examples", String.class, Example.class);
        topConstructor.addTypeDescription(parameterTD);

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(Example.class, ExampleImpl.class)
                .addRef());
        topConstructor.addTypeDescription(ExpandedTypeDescription.create(RequestBody.class, RequestBodyImpl.class)
                .addRef());
        topConstructor.addTypeDescription(new TypeDescription(Content.class, ContentImpl.class));

        TypeDescription mediaTypeTD = ExpandedTypeDescription.create(MediaType.class, MediaTypeImpl.class);
        mediaTypeTD.addPropertyParameters("encoding", String.class, Encoding .class);
        mediaTypeTD.addPropertyParameters("examples", String.class, Example.class);
        topConstructor.addTypeDescription(mediaTypeTD);

        TypeDescription encodingTD = ExpandedTypeDescription.create(Encoding.class, EncodingImpl.class)
                .addEnum("style", Encoding.Style::valueOf);
        encodingTD.addPropertyParameters("headers", String.class, Header.class);
        topConstructor.addTypeDescription(encodingTD);

        TypeDescription headerTD = ExpandedTypeDescription.create(Header.class, HeaderImpl.class)
                .addEnum("in", Parameter.In::valueOf)
                .addEnum("style", Parameter.Style::valueOf)
                .addRef();
        headerTD.addPropertyParameters("examples", String.class, Example.class);
        topConstructor.addTypeDescription(headerTD);

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(SecurityScheme.class, SecuritySchemeImpl.class)
                .addEnum("in", SecurityScheme.In::valueOf)
                .addEnum("type", SecurityScheme.Type::valueOf)
                .addRef());
        topConstructor.addTypeDescription(ExpandedTypeDescription.create(Link.class, LinkImpl.class)
                .addRef());

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(OAuthFlows.class, OAuthFlowsImpl.class));
        topConstructor.addTypeDescription(ExpandedTypeDescription.create(OAuthFlow.class, OAuthFlowImpl.class));
        topConstructor.addTypeDescription(ExpandedTypeDescription.create(Scopes.class, ScopesImpl.class));

        TypeDescription callbackTD = ExpandedTypeDescription.create(Callback.class, CallbackImpl.class)
                .addRef();
        callbackTD.addPropertyParameters("pathItems", String.class, PathItem.class);
        topConstructor.addTypeDescription(callbackTD);

        topConstructor.addTypeDescription(ExpandedTypeDescription.create(XML.class, XMLImpl.class));

        Yaml yaml = new Yaml(topConstructor);
        OpenAPI result = yaml.loadAs(input, OpenAPI.class);
        return result;
    }

    private static String getter(PathItem.HttpMethod method) {
        return methodName("get", method);
    }

    private static String setter(PathItem.HttpMethod method) {
        return methodName("set", method);
    }

    private static String methodName(String operation, PathItem.HttpMethod method) {
        return operation + method.name();
    }

}
