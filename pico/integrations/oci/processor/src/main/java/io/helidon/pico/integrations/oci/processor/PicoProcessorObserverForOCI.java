/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.integrations.oci.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.pico.processor.spi.PicoAnnotationProcessorObserver;
import io.helidon.pico.processor.spi.ProcessingEvent;
import io.helidon.pico.tools.TemplateHelper;
import io.helidon.pico.tools.ToolsException;

import jakarta.inject.Inject;

import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.pico.processor.GeneralProcessorUtils.findFirst;
import static io.helidon.pico.processor.GeneralProcessorUtils.isProviderType;

/**
 * Implementation that will monitor {@link io.helidon.pico.processor.PicoAnnotationProcessor} for all injection points
 * using the {@code OCI SDK Services} and translate those into code-generated {@link io.helidon.pico.api.Activator}s,
 * {@link io.helidon.pico.api.ModuleComponent}, etc.
 */
public class PicoProcessorObserverForOCI implements PicoAnnotationProcessorObserver {
    static final String OCI_ROOT_PACKAGE_NAME_PREFIX = "com.oracle.bmc.";
    static final String GENERATED_PREFIX = "generated.";
    static final String GENERATED_CLIENT_SUFFIX = "$$Oci$$Client";
    static final String GENERATED_CLIENT_BUILDER_SUFFIX = GENERATED_CLIENT_SUFFIX + "Builder";
    static final String GENERATED_OCI_ROOT_PACKAGE_NAME_PREFIX = GENERATED_PREFIX + OCI_ROOT_PACKAGE_NAME_PREFIX;
    static final String TAG_TEMPLATE_SERVICE_CLIENT_PROVIDER_NAME = "service-client-provider.hbs";
    static final String TAG_TEMPLATE_SERVICE_CLIENT_BUILDER_PROVIDER_NAME = "service-client-builder-provider.hbs";
    static final Set<String> TYPENAME_EXCEPTIONS =
            Set.of(OCI_ROOT_PACKAGE_NAME_PREFIX + "Region",
                   OCI_ROOT_PACKAGE_NAME_PREFIX + "auth.AbstractAuthenticationDetailsProvider",
                   OCI_ROOT_PACKAGE_NAME_PREFIX + "circuitbreaker.OciCircuitBreaker"
            );
    static final Set<String> NO_DOT_EXCEPTIONS =
            Set.of(OCI_ROOT_PACKAGE_NAME_PREFIX + "streaming.Stream",
                   OCI_ROOT_PACKAGE_NAME_PREFIX + "streaming.StreamAsync"
            );

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public PicoProcessorObserverForOCI() {
    }

    @Override
    public void onProcessingEvent(ProcessingEvent event) {
        ProcessingEnvironment processingEnv = event.processingEnvironment().orElseThrow();
        event.elementsOfInterest().stream()
                .filter(it -> shouldProcess(it, processingEnv))
                .forEach(it -> process(it, processingEnv));
    }

    private void process(TypedElementInfo element,
                         ProcessingEnvironment processingEnv) {
        if (TypeInfo.KIND_FIELD.equalsIgnoreCase(element.elementTypeKind())) {
            process(element.typeName(), processingEnv);
        } else if (TypeInfo.KIND_METHOD.equalsIgnoreCase(element.elementTypeKind())
                || TypeInfo.KIND_CONSTRUCTOR.equalsIgnoreCase(element.elementTypeKind())) {
            element.parameterArguments().stream()
                    .filter(it -> shouldProcess(it.typeName(), processingEnv))
                    .forEach(it -> process(it.typeName(), processingEnv));
        }
    }

    private void process(TypeName ociServiceTypeName,
                         ProcessingEnvironment processingEnv) {
        if (isProviderType(ociServiceTypeName)
                || ociServiceTypeName.isOptional()) {
            ociServiceTypeName = ociServiceTypeName.typeArguments().get(0);
        }
        assert (!ociServiceTypeName.generic()) : ociServiceTypeName.name();
        assert (ociServiceTypeName.name().startsWith(OCI_ROOT_PACKAGE_NAME_PREFIX)) : ociServiceTypeName.name();

        TypeName generatedOciServiceClientTypeName = toGeneratedServiceClientTypeName(ociServiceTypeName);
        String serviceClientBody = toBody(TAG_TEMPLATE_SERVICE_CLIENT_PROVIDER_NAME,
                                          ociServiceTypeName,
                                          generatedOciServiceClientTypeName);
        codegen(generatedOciServiceClientTypeName, serviceClientBody, processingEnv);

        TypeName generatedOciServiceClientBuilderTypeName = toGeneratedServiceClientBuilderTypeName(ociServiceTypeName);
        String serviceClientBuilderBody = toBody(TAG_TEMPLATE_SERVICE_CLIENT_BUILDER_PROVIDER_NAME,
                                          ociServiceTypeName,
                                          generatedOciServiceClientTypeName);
        codegen(generatedOciServiceClientBuilderTypeName, serviceClientBuilderBody, processingEnv);
    }

    private void codegen(TypeName typeName,
                         String body,
                         ProcessingEnvironment processingEnv) {
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject javaSrc = filer.createSourceFile(typeName.name());
            try (Writer os = javaSrc.openWriter()) {
                os.write(body);
            }
        } catch (FilerException x) {
            processingEnv.getMessager().printWarning("Failed to write java file: " + x);
        } catch (Exception x) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "Failed to write java file: " + x, x);
            processingEnv.getMessager().printError("Failed to write java file: " + x);
        }
    }

    static String toBody(String templateName,
                          TypeName ociServiceTypeName,
                          TypeName generatedOciActivatorTypeName) {
        TemplateHelper templateHelper = TemplateHelper.create();
        String template = loadTemplate(templateName);
        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", ociServiceTypeName.name());
        subst.put("simpleclassname", ociServiceTypeName.className());
        subst.put("packagename", generatedOciActivatorTypeName.packageName());
        subst.put("generatedanno", templateHelper.generatedStickerFor(PicoAnnotationProcessorObserver.class.getName()));
        subst.put("dot", maybeDot(ociServiceTypeName));
        subst.put("usesRegion", usesRegion(ociServiceTypeName));
        return templateHelper.applySubstitutions(template, subst, true).trim();
    }

    static String maybeDot(TypeName ociServiceTypeName) {
        return NO_DOT_EXCEPTIONS.contains(ociServiceTypeName.name()) ? "" : ".";
    }

    static boolean usesRegion(TypeName ociServiceTypeName) {
        // it turns out that the same exceptions used for dotting the builder also applies to whether it uses region
        return !NO_DOT_EXCEPTIONS.contains(ociServiceTypeName.name());
    }

    static String loadTemplate(String name) {
        String path = "templates/pico/oci/default/" + name;
        try {
            InputStream in = PicoProcessorObserverForOCI.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                throw new IOException("Could not find template " + path + " on classpath.");
            }
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new ToolsException(e.getMessage(), e);
        }
    }

    static boolean shouldProcess(TypedElementInfo element,
                                 ProcessingEnvironment processingEnv) {
        if (findFirst(Inject.class, element.annotations()).isEmpty()) {
            return false;
        }

        if (TypeInfo.KIND_FIELD.equalsIgnoreCase(element.elementTypeKind())) {
            return shouldProcess(element.typeName(), processingEnv);
        } else if (TypeInfo.KIND_METHOD.equalsIgnoreCase(element.elementTypeKind())
                || TypeInfo.KIND_CONSTRUCTOR.equalsIgnoreCase(element.elementTypeKind())) {
            return element.parameterArguments().stream()
                    .anyMatch(it -> shouldProcess(it.typeName(), processingEnv));
        }

        return false;
    }

    static boolean shouldProcess(TypeName typeName,
                                 ProcessingEnvironment processingEnv) {
        if ((typeName.typeArguments().size() > 0)
                && (isProviderType(typeName) || typeName.isOptional())) {
            typeName = typeName.typeArguments().get(0);
        }

        String name = typeName.name();
        if (!name.startsWith(OCI_ROOT_PACKAGE_NAME_PREFIX)
                || TYPENAME_EXCEPTIONS.contains(name)
                || name.endsWith(".Builder")
                || name.endsWith("Client")
                || name.endsWith("ClientBuilder")) {
            return false;
        }

        // check to see if we already generated it before, and if so we can skip creating it again
        String generatedTypeName = toGeneratedServiceClientTypeName(typeName).name();
        TypeElement typeElement = processingEnv.getElementUtils()
                .getTypeElement(generatedTypeName);
        return (typeElement == null);
    }

    private static TypeName toGeneratedServiceClientTypeName(TypeName typeName) {
        return create(GENERATED_PREFIX + typeName.packageName(),
                      typeName.className() + GENERATED_CLIENT_SUFFIX);
    }

    private static TypeName toGeneratedServiceClientBuilderTypeName(TypeName typeName) {
        return create(GENERATED_PREFIX + typeName.packageName(),
                      typeName.className() + GENERATED_CLIENT_BUILDER_SUFFIX);
    }

}
