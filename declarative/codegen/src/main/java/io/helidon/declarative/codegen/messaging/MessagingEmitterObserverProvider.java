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

package io.helidon.declarative.codegen.messaging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.Api;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.InjectCodegenObserver;
import io.helidon.service.codegen.spi.InjectCodegenObserverProvider;

/**
 * Provider that turns {@code @Service.Named Emitter<T>} injection points into generated emitter services.
 */
public class MessagingEmitterObserverProvider implements InjectCodegenObserverProvider {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public MessagingEmitterObserverProvider() {
    }

    @Override
    public InjectCodegenObserver create(RegistryCodegenContext context) {
        return new MessagingEmitterObserver();
    }

    private static final class MessagingEmitterObserver implements InjectCodegenObserver {
        private static final TypeName GENERATOR = TypeName.create(MessagingEmitterObserver.class);

        private final Map<String, TypeName> emitterPayloadTypes = new HashMap<>();
        private final Map<String, String> emitterTypeIdentities = new HashMap<>();

        @Override
        public void onInjectionPoint(RegistryRoundContext roundContext,
                                     TypeInfo service,
                                     TypedElementInfo element,
                                     TypedElementInfo argument) {
            TypeName typeName = argument.typeName();
            if (!typeName.genericTypeName().equals(MessagingTypes.EMITTER)) {
                return;
            }
            if (typeName.typeArguments().size() != 1) {
                throw new CodegenException("Messaging emitters must declare exactly one payload type argument",
                                           argument.originatingElementValue());
            }

            String channel = argument.findAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED)
                    .flatMap(Annotation::stringValue)
                    .orElseThrow(() -> new CodegenException("Messaging emitters must be qualified with @Service.Named",
                                                            argument.originatingElementValue()));
            validateQualifiers(argument);
            validateChannel(channel, argument);

            TypeName payloadType = typeName.typeArguments().getFirst();
            if (!MessagingExtension.isConcretePayloadType(payloadType)
                    || hasRawGenericUsage(roundContext, payloadType)) {
                throw new CodegenException("Messaging emitter payload type must be concrete: " + payloadType,
                                           argument.originatingElementValue());
            }
            MessagingExtension.validateAccessibleType(roundContext,
                                                      payloadType,
                                                      service.typeName().packageName(),
                                                      argument.originatingElementValue(),
                                                      "Messaging emitter payload type");
            TypeName generatedType = emitterTypeName(service.typeName(), channel, argument);
            TypeName registeredPayloadType = emitterPayloadTypes.putIfAbsent(channel, payloadType);
            if (registeredPayloadType != null
                    && !registeredPayloadType.resolvedName().equals(payloadType.resolvedName())) {
                throw new CodegenException("Conflicting messaging emitter payload types for channel "
                                                   + channel + ": " + registeredPayloadType + " and " + payloadType,
                                           argument.originatingElementValue());
            }
            if (roundContext.generatedType(generatedType).isEmpty()) {
                generateEmitter(roundContext, service, generatedType, payloadType, channel);
            }
        }

        private TypeName emitterTypeName(TypeName serviceType,
                                         String channel,
                                         TypedElementInfo originatingElement) {
            String identity = producerId(serviceType, channel);
            String candidate = MessagingExtension.generatedEmitterClassName(serviceType, identity);
            String generatedTypeName = serviceType.packageName() + "." + candidate;
            String existing = emitterTypeIdentities.putIfAbsent(generatedTypeName, identity);
            if (existing != null && !existing.equals(identity)) {
                throw new CodegenException("Generated messaging emitter name collision between "
                                                   + existing + " and " + identity,
                                           originatingElement.originatingElementValue());
            }
            return TypeName.builder()
                    .packageName(serviceType.packageName())
                    .className(candidate)
                    .build();
        }

        private void generateEmitter(RegistryRoundContext roundContext,
                                     TypeInfo serviceInfo,
                                     TypeName generatedType,
                                     TypeName payloadType,
                                     String channel) {
            TypeName messageType = messageType(payloadType);

            ClassModel.Builder classModel = ClassModel.builder()
                    .copyright(CodegenUtil.copyright(GENERATOR, serviceInfo.typeName(), generatedType))
                    .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                                   serviceInfo.typeName(),
                                                                   generatedType,
                                                                   "1",
                                                                   ""))
                    .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                    .type(generatedType)
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .description("Messaging emitter service for channel <code>"
                                         + MessagingExtension.escapeJavadoc(channel) + "</code>.")
                    .addInterface(emitterInterface(payloadType))
                    .addInterface(MessagingTypes.EMITTER_REGISTRATION)
                    .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON))
                    .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED, channel));

            classModel.addField(registry -> registry
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(messagingRuntimeSupplierType())
                    .name("registry"));

            classModel.addConstructor(ctr -> ctr
                    .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addParameter(registry -> registry
                            .type(messagingRuntimeSupplierType())
                            .name("registry"))
                    .addContentLine("this.registry = registry;"));

            classModel.addMethod(method -> method
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeName.create(void.class))
                    .name("emit")
                    .addParameter(messages -> messages
                            .type(messageBatchType(payloadType))
                            .name("messages"))
                    .addContent("registry.get().emitBatch(")
                    .addContentLiteral(channel)
                    .addContentLine(", messages);"));

            addLiteralMethod(classModel, "channel", channel);
            addLiteralMethod(classModel,
                             "producerId",
                             producerId(serviceInfo.typeName(), channel));
            addGenericTypeField(classModel, "PAYLOAD_GENERIC_TYPE", payloadType.boxed());
            addGenericTypeMethod(classModel, "payloadGenericType", "PAYLOAD_GENERIC_TYPE");
            addGenericTypeField(classModel, "ENVELOPE_GENERIC_TYPE", messageType);
            addGenericTypeMethod(classModel, "envelopeGenericType", "ENVELOPE_GENERIC_TYPE");

            roundContext.addGeneratedType(generatedType, classModel, serviceInfo.typeName(), serviceInfo);
        }

        private void addLiteralMethod(ClassModel.Builder classModel, String name, String value) {
            classModel.addMethod(method -> method
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.STRING)
                    .name(name)
                    .addContent("return ")
                    .addContentLiteral(value)
                    .addContentLine(";"));
        }

        private void addGenericTypeField(ClassModel.Builder classModel, String fieldName, TypeName type) {
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(genericType(type))
                    .name(fieldName)
                    .addContent("new ")
                    .addContent(genericType(type))
                    .addContent("() { }"));
        }

        private void addGenericTypeMethod(ClassModel.Builder classModel, String name, String fieldName) {
            classModel.addMethod(method -> method
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(genericType(TypeNames.WILDCARD))
                    .name(name)
                    .addContent("return ")
                    .addContent(fieldName)
                    .addContentLine(";"));
        }

        private TypeName genericType(TypeName type) {
            return TypeName.builder(TypeNames.GENERIC_TYPE)
                    .addTypeArgument(type)
                    .build();
        }

        private TypeName emitterInterface(TypeName payloadType) {
            return TypeName.builder()
                    .from(MessagingTypes.EMITTER)
                    .addTypeArgument(payloadType)
                    .build();
        }

        private TypeName messageType(TypeName payloadType) {
            return TypeName.builder()
                    .from(MessagingTypes.MESSAGE)
                    .addTypeArgument(payloadType)
                    .build();
        }

        private TypeName messageBatchType(TypeName payloadType) {
            return TypeName.builder(MessagingTypes.MESSAGE_BATCH)
                    .addTypeArgument(TypeName.builder()
                                             .generic(true)
                                             .wildcard(true)
                                             .className("?")
                                             .addUpperBound(payloadType)
                                             .build())
                    .build();
        }

        private TypeName messagingRuntimeSupplierType() {
            return TypeName.builder(TypeNames.SUPPLIER)
                    .addTypeArgument(MessagingTypes.MESSAGING_RUNTIME)
                    .build();
        }

        private boolean hasRawGenericUsage(RegistryRoundContext roundContext, TypeName typeName) {
            if (typeName.typeArguments().isEmpty()) {
                TypeInfo typeInfo = roundContext.typeInfo(typeName.genericTypeName()).orElse(null);
                if (typeInfo != null
                        && (!typeInfo.declaredType().typeArguments().isEmpty()
                        || !typeInfo.declaredType().typeParameters().isEmpty())) {
                    return true;
                }
            }
            return typeName.typeArguments()
                    .stream()
                    .anyMatch(it -> hasRawGenericUsage(roundContext, it));
        }

        private void validateChannel(String channel, TypedElementInfo argument) {
            if (channel.isBlank()) {
                throw new CodegenException("Messaging emitter channel must not be blank",
                                           argument.originatingElementValue());
            }
            if (channel.equals("*")) {
                throw new CodegenException("Messaging emitter channel must not use the reserved Service.Named wildcard *",
                                           argument.originatingElementValue());
            }
            if (!channel.equals(channel.strip())) {
                throw new CodegenException("Messaging emitter channel must not have leading or trailing whitespace",
                                           argument.originatingElementValue());
            }
            if (channel.codePoints().anyMatch(Character::isISOControl)) {
                throw new CodegenException("Messaging emitter channel must not contain control characters",
                                           argument.originatingElementValue());
            }
        }

        private void validateQualifiers(TypedElementInfo argument) {
            List<Annotation> qualifiers = argument.annotations()
                    .stream()
                    .filter(annotation -> annotation.hasMetaAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_QUALIFIER))
                    .toList();
            if (qualifiers.size() != 1
                    || !qualifiers.getFirst().typeName().equals(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED)) {
                throw new CodegenException("Messaging emitters support only a single @Service.Named qualifier",
                                           argument.originatingElementValue());
            }
        }

        private String producerId(TypeName serviceType, String channel) {
            return serviceType.fqName() + "#emitter:" + channel;
        }
    }
}
