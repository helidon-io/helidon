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

package io.helidon.integrations.langchain4j.codegen;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.common.types.AccessModifier.PRIVATE;
import static io.helidon.common.types.AccessModifier.PUBLIC;
import static io.helidon.common.types.TypeNames.CLASS_WILDCARD;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.STRING;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AGENTS_CONFIG;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AGENT_METADATA;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_AGENT;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY_PROVIDER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CONTENT_RETRIEVER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_MCP_CLIENTS;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_RETRIEVER_AUGMENTOR;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_TOOLS;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_TOOL_PROVIDER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.SVC_SERVICES_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIED_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIER;

class AgentMetadataSupplierBuilder {
    private static final TypeName GENERATOR = TypeName.create(AgentMetadataSupplierBuilder.class);

    private static final Map<TypeName, AnnotationMapper> AI_AGENT_ANNOTATIONS;

    static {
        var m = new LinkedHashMap<TypeName, AnnotationMapper>();
        m.put(AI_AGENT, new AnnotationMapper("name", MapperType.STRING_VALUE));
        m.put(AI_CHAT_MODEL, new AnnotationMapper("chatModel", MapperType.STRING_VALUE));
        m.put(AI_CHAT_MEMORY, new AnnotationMapper("chatMemory", MapperType.STRING_VALUE));
        m.put(AI_CHAT_MEMORY_PROVIDER, new AnnotationMapper("chatMemoryProvider", MapperType.STRING_VALUE));
        m.put(AI_CONTENT_RETRIEVER, new AnnotationMapper("contentRetriever", MapperType.STRING_VALUE));
        m.put(AI_RETRIEVER_AUGMENTOR, new AnnotationMapper("retrievalAugmentor", MapperType.STRING_VALUE));
        m.put(AI_TOOL_PROVIDER, new AnnotationMapper("toolProvider", MapperType.STRING_VALUE));
        m.put(AI_TOOLS, new AnnotationMapper("tools", MapperType.CLASSES_VALUE));
        m.put(AI_MCP_CLIENTS, new AnnotationMapper("mcpClients", MapperType.STRINGS_VALUE));
        AI_AGENT_ANNOTATIONS = Collections.unmodifiableMap(m);
    }

    private AgentMetadataSupplierBuilder() {
    }

    static void build(TypeInfo agentTypeInfo, RoundContext ctx) {
        TypeName generatedType = TypeName.builder()
                .packageName(agentTypeInfo.typeName().packageName())
                .className(agentTypeInfo.typeName().className() + "__AiAgent__Metadata")
                .build();

        var agentName = agentTypeInfo
                .findAnnotation(AI_AGENT)
                .flatMap(Annotation::stringValue)
                .orElseThrow(() -> new IllegalStateException("Agent " + agentTypeInfo.typeName()
                        .name() + " missing required @Ai.Agent annotation!"));

        var classModel = ClassModel.builder()
                .type(generatedType)
                .addInterface(TypeName.builder(SVC_SERVICES_FACTORY).addTypeArgument(AGENT_METADATA).build())
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 generatedType,
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               generatedType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_SINGLETON))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        var buildTimeConfigMethod = Method.builder()
                .name("buildTimeConfig")
                .accessModifier(PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(AGENTS_CONFIG)
                .addContent("return ")
                .addContent(AGENTS_CONFIG)
                .addContentLine(".builder()")
                .increaseContentPadding();

        AI_AGENT_ANNOTATIONS.forEach((annotationType, mapper) -> {
            mapper.add(buildTimeConfigMethod, agentTypeInfo.findAnnotation(annotationType));
        });
        buildTimeConfigMethod.addContentLine(".build();")
                .decreaseContentPadding();

        var innerMetaDataClass = InnerClass.builder()
                .name(agentTypeInfo.typeName().className() + "Metadata")
                .addInterface(AGENT_METADATA)
                .accessModifier(PRIVATE)
                .addMethod(buildTimeConfigMethod)
                .addMethod(mb -> mb
                        .name("agentClass")
                        .addAnnotation(Annotations.OVERRIDE)
                        .returnType(CLASS_WILDCARD)
                        .addContent("return ")
                        .addContent(agentTypeInfo.typeName())
                        .addContentLine(".class;")
                )
                .addMethod(mb -> mb
                        .name("agentName")
                        .addAnnotation(Annotations.OVERRIDE)
                        .returnType(STRING)
                        .addContent("return ")
                        .addContentLiteral(agentName)
                        .addContentLine(";")
                )
                .build();

        classModel.addInnerClass(innerMetaDataClass);

        var getMethod = Method.builder()
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(PUBLIC)
                .name("services")
                .returnType(TypeName.builder(LIST)
                                    .addTypeArgument(TypeName.builder(SERVICE_QUALIFIED_INSTANCE).addTypeArgument(AGENT_METADATA)
                                                             .build())
                                    .build())
                //        return List.of(Service.QualifiedInstance
                //                .create(new HelidonExpertAgentMetadata(),
                //                        Qualifier.createNamed(HelidonExpertAgent.class)));
                .addContent("return ")
                .addContent(LIST)
                .addContentLine(".of(")
                .increaseContentPadding()
                .addContent(SERVICE_QUALIFIED_INSTANCE)
                .addContentLine(".create(")
                .increaseContentPadding()
                .addContent("new ")
                .addContent(innerMetaDataClass.name())
                .addContentLine("(), ")
                .addContent(SERVICE_QUALIFIER)
                .addContent(".createNamed(")
                .addContent(agentTypeInfo.typeName())
                .addContentLine(".class)")
                .decreaseContentPadding()
                .addContentLine(")")
                .decreaseContentPadding()
                .addContentLine(");");

        classModel.addMethod(getMethod);

        ctx.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private record AnnotationMapper(String property, MapperType mapperType) {

        void add(Method.Builder b, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Annotation> a) {
            if (a.isEmpty()) {
                return;
            }
            switch (mapperType) {
            case STRING_VALUE -> {
                var val = a.get().stringValue();
                if (val.isEmpty()) {
                    return;
                }
                b.addContent(".")
                        .addContent(property)
                        .addContent("(")
                        .addContentLiteral(val.get())
                        .addContentLine(")");
            }
            case STRINGS_VALUE -> {
                var val = a.get().stringValues();
                if (val.isEmpty()) {
                    return;
                }
                b.addContent(".")
                        .addContent(property)
                        .addContent("(")
                        .addContent(Set.class)
                        .addContent(val.get().stream()
                                            .map(s -> '"' + s + '"')
                                            .collect(Collectors.joining(", ", ".of(", ")")))
                        .addContentLine(")");
            }
            case CLASSES_VALUE -> {
                var val = a.get().typeValues();
                if (val.isEmpty()) {
                    return;
                }
                b.addContent(".")
                        .addContent(property)
                        .addContent("(")
                        .addContent(Set.class).addContent(".of(")
                        .update(it -> {
                            Iterator<TypeName> iterator = val.get().iterator();
                            while (iterator.hasNext()) {
                                it.addContent(iterator.next())
                                        .addContent(".class");
                                if (iterator.hasNext()) {
                                    it.addContent(", ");
                                }
                            }
                        })
                        .addContent("))");
            }
            default -> throw new IllegalArgumentException("Unexpected mapperType: " + mapperType);
            }
        }

    }

    private enum MapperType {
        STRING_VALUE,
        STRINGS_VALUE,
        CLASSES_VALUE;
    }
}
