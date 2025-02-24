/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.codegen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.InjectCodegenObserver;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIED_INSTANCE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;

/**
 * This processor is an implementation of {@link io.helidon.service.codegen.spi.InjectCodegenObserver}.
 * When on the APT classpath, it will monitor
 * injection processor for all injection points that are
 * using the {@code OCI SDK Services} and translate those injection points into code-generated
 * {@code ServiceDescriptors}s, {@code ModuleComponent}, etc. for those services / components.
 * This process will therefore make the {@code OCI SDK} set of services injectable by your (non-MP-based) Helidon application, and
 * be tailored to exactly what is actually used by your application from the SDK.
 * <p>
 * For example, if your code had this:
 * <pre>
 * {@code
 *   @Inject
 *   com.oracle.bmc.ObjectStorage objStore;
 * }
 * </pre>
 * This would result in code generating the necessary artifacts at compile time that will make {@code ObjectStorage} injectable.
 * <p>
 * All injection points using the same package name as the OCI SDK (e.g., {@code com.oracle.bmc} as shown with ObjectStorage in
 * the case above) will be observed and processed and eventually result in code generation into your
 * {@code target/generated-sources} directory. This is the case for any artifact that is attempted to be injected unless there is
 * found a configuration signaling an exception to avoid the code generation for the activator.
 * <p>
 * The processor will allow exceptions in one of three ways:
 * <ul>
 *     <li>via the code directly here - see the {@link #shouldProcess} method.</li>
 *     <li>via resources on the classpath - the implementation looks for files named {@link #TYPENAME_EXCEPTIONS_FILENAME}
 *     in {@code META-INF/helidon/oci/sdk/codegen}, and will read those resources during initialization.
 *     Each line of this file would be a fully qualified type name to avoid processing that type name.</li>
 *     <li>via {@code -A} directives on the compiler command line. Using the same tag as referenced above. The line can be
 *     comma-delimited, and each token will be treated as a fully qualified type name to signal that the type should be
 *     not be processed.</li>
 * </ul>
 */
class OciInjectionCodegenObserver implements InjectCodegenObserver {
    static final String OCI_ROOT_PACKAGE_NAME_PREFIX = "com.oracle.bmc.";

    // all generated sources will have this package prefix
    static final String GENERATED_PREFIX = "io.helidon.integrations.generated.";
    // all generated sources will have this class name suffix
    static final String GENERATED_CLIENT_SUFFIX = "__Oci_Client";
    static final String GENERATED_CLIENT_BUILDER_SUFFIX = GENERATED_CLIENT_SUFFIX + "Builder";
    private static final double DEFAULT_INJECT_WEIGHT = Weighted.DEFAULT_WEIGHT - 1;
    private static final TypeName PROCESSOR_TYPE = TypeName.create(OciInjectionCodegenObserver.class);
    private static final String TYPENAME_EXCEPTIONS_FILENAME = "codegen-exclusions.txt";
    private static final String NO_DOT_EXCEPTIONS_FILENAME = "builder-name-exceptions.txt";
    private static final Set<TypeName> FACTORIES = Set.of(
            TypeNames.SUPPLIER,
            ServiceCodegenTypes.SERVICE_SERVICES_FACTORY,
            ServiceCodegenTypes.SERVICE_INJECTION_POINT_FACTORY,
            ServiceCodegenTypes.SERVICE_QUALIFIED_FACTORY);

    private final Set<String> typenameExceptions;
    private final Set<String> noDotExceptions;
    private final RegistryCodegenContext ctx;

    OciInjectionCodegenObserver(RegistryCodegenContext ctx) {
        this.ctx = ctx;

        CodegenOptions options = ctx.options();

        Set<String> typenameExceptions = new HashSet<>();
        typenameExceptions.addAll(OciInjectCodegenObserverProvider.OPTION_TYPENAME_EXCEPTIONS.value(options));
        typenameExceptions.addAll(loadMetaConfig(TYPENAME_EXCEPTIONS_FILENAME));
        this.typenameExceptions = typenameExceptions;

        Set<String> noDotExceptions = new HashSet<>();
        noDotExceptions.addAll(OciInjectCodegenObserverProvider.OPTION_NO_DOT_EXCEPTIONS.value(options));
        noDotExceptions.addAll(loadMetaConfig(NO_DOT_EXCEPTIONS_FILENAME));
        this.noDotExceptions = noDotExceptions;
    }

    static TypeName toGeneratedServiceClientTypeName(TypeName typeName) {
        return TypeName.builder()
                .packageName(GENERATED_PREFIX + typeName.packageName())
                .className(typeName.className() + GENERATED_CLIENT_SUFFIX)
                .build();
    }

    static TypeName toGeneratedServiceClientBuilderTypeName(TypeName typeName) {
        return TypeName.builder()
                .packageName(GENERATED_PREFIX + typeName.packageName())
                .className(typeName.className() + GENERATED_CLIENT_BUILDER_SUFFIX)
                .build();
    }

    @Override
    public void onProcessingEvent(RegistryRoundContext roundContext, Set<TypedElementInfo> elements) {
        elements.stream()
                .filter(this::shouldProcess)
                .forEach(it -> process(roundContext, it));
    }

    Set<String> typenameExceptions() {
        return typenameExceptions;
    }

    Set<String> noDotExceptions() {
        return noDotExceptions;
    }

    ClassModel.Builder toBuilderBody(TypeName ociServiceTypeName,
                                     TypeName generatedOciService,
                                     TypeName generatedOciServiceBuilderTypeName) {
        boolean usesRegion = usesRegion(ociServiceTypeName);

        String maybeDot = maybeDot(ociServiceTypeName);
        String builderSuffix = "Client" + maybeDot + "Builder";

        ClassModel.Builder classModel = ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .copyright(CodegenUtil.copyright(PROCESSOR_TYPE,
                                                 ociServiceTypeName,
                                                 generatedOciService))
                .addAnnotation(CodegenUtil.generatedAnnotation(PROCESSOR_TYPE,
                                                               ociServiceTypeName,
                                                               generatedOciService,
                                                               "1",
                                                               ""))
                .type(generatedOciServiceBuilderTypeName)
                .addInterface(ipProvider(ociServiceTypeName, builderSuffix))
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON))
                .addAnnotation(Annotation.builder()
                                       .typeName(TypeName.create(Weight.class))
                                       .putValue("value", DEFAULT_INJECT_WEIGHT)
                                       .build());

        // fields
        if (usesRegion) {
            TypeName regionProviderType = ipProvider(TypeName.create("com.oracle.bmc.Region"), "");
            classModel.addField(regionProvider -> regionProvider
                    .isFinal(true)
                    .accessModifier(AccessModifier.PRIVATE)
                    .name("regionProvider")
                    .type(regionProviderType));
            // constructor
            classModel.addConstructor(ctor -> ctor
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                    .addAnnotation(Annotation.create(Deprecated.class))
                    .addParameter(regionProvider -> regionProvider.name("regionProvider")
                            .type(regionProviderType))
                    .addContentLine("this.regionProvider = regionProvider;"));
        } else {
            // constructor
            classModel.addConstructor(ctor -> ctor
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                    .addAnnotation(Annotation.create(Deprecated.class)));
        }

        String clientType = "@" + ociServiceTypeName.fqName() + "Client@";

        // method(s)
        classModel.addMethod(first -> first
                .name("first")
                .addAnnotation(Annotation.create(Override.class))
                .returnType(optionalQualifiedInstance(ociServiceTypeName, builderSuffix))
                .addParameter(query -> query.name("query")
                        .type(ServiceCodegenTypes.SERVICE_LOOKUP))
                .update(it -> {
                    if (usesRegion) {
                        it.addContentLine("var builder = " + clientType + ".builder();")
                                .addContent("regionProvider.first(query).map(")
                                .addContent(SERVICE_QUALIFIED_INSTANCE)
                                .addContentLine("::get).ifPresent(builder::region);")
                                .addContent("return ")
                                .addContent(Optional.class)
                                .addContent(".of(")
                                .addContent(SERVICE_QUALIFIED_INSTANCE)
                                .addContent(".create(")
                                .addContentLine("builder));");
                    } else {
                        it.addContent("return ")
                                .addContent(Optional.class)
                                .addContent(".of(")
                                .addContent(SERVICE_QUALIFIED_INSTANCE)
                                .addContent(".create(")
                                .addContent(clientType)
                                .addContent(".builder());");
                    }
                }));

        return classModel;
    }

    ClassModel.Builder toBody(TypeName ociServiceTypeName,
                              TypeName generatedOciService) {
        ClassModel.Builder classModel = ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .copyright(CodegenUtil.copyright(PROCESSOR_TYPE,
                                                 ociServiceTypeName,
                                                 generatedOciService))
                .addAnnotation(CodegenUtil.generatedAnnotation(PROCESSOR_TYPE,
                                                               ociServiceTypeName,
                                                               generatedOciService,
                                                               "1",
                                                               ""))
                .type(generatedOciService)
                .addInterface(ipProvider(ociServiceTypeName, "Client"))
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON))
                .addAnnotation(Annotation.builder()
                                       .typeName(TypeName.create(Weight.class))
                                       .putValue("value", DEFAULT_INJECT_WEIGHT)
                                       .build())
                .addAnnotation(Annotation.builder()
                                       .typeName(ServiceCodegenTypes.SERVICE_ANNOTATION_EXTERNAL_CONTRACTS)
                                       .putValue("value", ociServiceTypeName)
                                       .build());

        TypeName authProviderType = ipProvider(TypeName.create("com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider"), "");
        TypeName builderProviderType = ipProvider(ociServiceTypeName, "Client" + maybeDot(ociServiceTypeName) + "Builder");

        // fields
        classModel.addField(authProvider -> authProvider
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .name("authProvider")
                .type(authProviderType));

        classModel.addField(builderProvider -> builderProvider
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .name("builderProvider")
                .type(builderProviderType));

        // constructor
        classModel.addConstructor(ctor -> ctor
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .addAnnotation(Annotations.DEPRECATED)
                .addParameter(authProvider -> authProvider.name("authProvider")
                        .type(authProviderType))
                .addParameter(builderProvider -> builderProvider.name("builderProvider")
                        .type(builderProviderType))
                .addContentLine("this.authProvider = authProvider;")
                .addContentLine("this.builderProvider = builderProvider;"));

        // method(s)
        classModel.addMethod(first -> first
                .name("first")
                .addAnnotation(Annotation.create(Override.class))
                .returnType(optionalQualifiedInstance(ociServiceTypeName, "Client"))
                .addParameter(query -> query.name("query")
                        .type(ServiceCodegenTypes.SERVICE_LOOKUP))
                .addContent("return ")
                .addContent(Optional.class)
                .addContent(".of(")
                .addContent(SERVICE_QUALIFIED_INSTANCE)
                .addContent(".create(")
                .addContentLine("builderProvider.first(query).orElseThrow().get().build(authProvider.first"
                                + "(query).orElseThrow().get())));"));

        return classModel;
    }

    boolean usesRegion(TypeName ociServiceTypeName) {
        // it turns out that the same exceptions used for dotting the builder also applies to whether it uses region
        return !noDotExceptions.contains(ociServiceTypeName.name());
    }

    String maybeDot(TypeName ociServiceTypeName) {
        return noDotExceptions.contains(ociServiceTypeName.name()) ? "" : ".";
    }

    boolean shouldProcess(TypeName typeName) {
        if (!typeName.typeArguments().isEmpty()
                && isFactory(typeName) || typeName.isOptional()) {
            typeName = typeName.typeArguments().getFirst();
        }

        String name = typeName.resolvedName();
        if (!name.startsWith(OCI_ROOT_PACKAGE_NAME_PREFIX)
                || name.endsWith(".Builder")
                || name.endsWith("Client")
                || name.endsWith("ClientBuilder")
                || typenameExceptions.contains(name)) {
            return false;
        }

        // check to see if we already generated it before, and if so we can skip creating it again
        TypeName generatedTypeName = toGeneratedServiceClientTypeName(typeName);
        return ctx.typeInfo(generatedTypeName).isEmpty();
    }

    private boolean isFactory(TypeName typeName) {
        return FACTORIES.contains(typeName);
    }

    private static Set<String> loadMetaConfig(String fileName) {
        String path = "META-INF/helidon/oci/sdk/codegen/" + fileName;

        Set<String> result = new HashSet<>();
        try {
            Enumeration<URL> resources = OciInjectionCodegenObserver.class.getClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), UTF_8))) {
                    reader.lines()
                            .map(String::trim) // trim to text
                            .filter(not(String::isEmpty)) // filter empty lines
                            .filter(not(s -> s.startsWith("#"))) // ignore comments
                            .forEach(result::add);
                }
            }
        } catch (IOException e) {
            throw new CodegenException("Failed to load " + path + " from classpath", e);
        }
        return result;
    }

    private static TypeName optionalQualifiedInstance(TypeName typeName, String suffix) {
        return TypeName.builder(io.helidon.common.types.TypeNames.OPTIONAL)
                .addTypeArgument(TypeName.builder(SERVICE_QUALIFIED_INSTANCE)
                                         .addTypeArgument(TypeName.create(typeName.fqName() + suffix))
                                         .build())
                .build();
    }

    private static TypeName ipProvider(TypeName provided, String suffix) {
        return TypeName.builder(ServiceCodegenTypes.SERVICE_INJECTION_POINT_FACTORY)
                .addTypeArgument(TypeName.create(provided.fqName() + suffix))
                .build();
    }

    private boolean shouldProcess(TypedElementInfo element) {
        if (!element.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT)) {
            return false;
        }

        return switch (element.kind()) {
            case FIELD -> shouldProcess(element.typeName());
            case METHOD, CONSTRUCTOR -> element.parameterArguments().stream()
                    .anyMatch(it -> shouldProcess(it.typeName()));
            default -> false;
        };
    }

    private void process(RegistryRoundContext roundCtx, TypedElementInfo element) {
        switch (element.kind()) {
        case FIELD -> process(roundCtx, element.typeName());
        case METHOD, CONSTRUCTOR -> element.parameterArguments().stream()
                .filter(it -> shouldProcess(it.typeName()))
                .forEach(it -> process(roundCtx, it.typeName()));
        default -> {
        }
        }
    }

    private void process(RegistryRoundContext roundCtx, TypeName ociServiceTypeName) {
        if (isFactory(ociServiceTypeName)
                || ociServiceTypeName.isOptional()) {
            ociServiceTypeName = ociServiceTypeName.typeArguments().getFirst();
        }
        assert (!ociServiceTypeName.generic()) : ociServiceTypeName.name();
        assert (ociServiceTypeName.name().startsWith(OCI_ROOT_PACKAGE_NAME_PREFIX)) : ociServiceTypeName.name();

        TypeName generatedOciServiceClientTypeName = toGeneratedServiceClientTypeName(ociServiceTypeName);
        if (roundCtx.generatedType(generatedOciServiceClientTypeName).isEmpty()) {
            // only code generate once
            ClassModel.Builder serviceClient = toBody(ociServiceTypeName,
                                                      generatedOciServiceClientTypeName);
            roundCtx.addGeneratedType(generatedOciServiceClientTypeName, serviceClient, ociServiceTypeName);
        }

        TypeName generatedOciServiceClientBuilderTypeName = toGeneratedServiceClientBuilderTypeName(ociServiceTypeName);
        if (roundCtx.generatedType(generatedOciServiceClientBuilderTypeName).isEmpty()) {
            // only code generate once
            ClassModel.Builder serviceClientBuilder = toBuilderBody(ociServiceTypeName,
                                                                    generatedOciServiceClientTypeName,
                                                                    generatedOciServiceClientBuilderTypeName);
            roundCtx.addGeneratedType(generatedOciServiceClientBuilderTypeName, serviceClientBuilder, ociServiceTypeName);
        }
    }
}
