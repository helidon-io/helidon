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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.pico.processor.GeneralProcessorUtils;
import io.helidon.pico.processor.spi.PicoAnnotationProcessorObserver;
import io.helidon.pico.processor.spi.ProcessingEvent;
import io.helidon.pico.tools.TemplateHelper;
import io.helidon.pico.tools.ToolsException;

import jakarta.inject.Inject;

/**
 * Implementation that will monitor {@link io.helidon.pico.processor.PicoAnnotationProcessor} for all injection points
 * using the {@code OCI SDK Services} and translate those into code-generated {@link io.helidon.pico.api.Activator}s,
 * {@link io.helidon.pico.api.ModuleComponent}, etc.
 */
public class PicoProcessorObserverForOCI implements PicoAnnotationProcessorObserver {

    static final String OCI_PACKAGE_NAME_PREFIX = "com.oracle.bmc.";
    static final String TAG_TEMPLATE_SERVICE_PROVIDER_NAME = "service-provider.hbs";

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
                         ProcessingEnvironment proccessingEnv) {
        if (TypeInfo.KIND_FIELD.equalsIgnoreCase(element.elementTypeKind())) {
            process(element.typeName(), proccessingEnv);
        } else if (TypeInfo.KIND_METHOD.equalsIgnoreCase(element.elementTypeKind())
                || TypeInfo.KIND_CONSTRUCTOR.equalsIgnoreCase(element.elementTypeKind())) {
            element.parameterArguments().stream()
                    .filter(it -> shouldProcess(it.typeName(), proccessingEnv))
                    .forEach(it -> process(it.typeName(), proccessingEnv));
        }
    }

    private void process(TypeName ociServiceTypeName,
                         ProcessingEnvironment proccessingEnv) {
        if (GeneralProcessorUtils.isProviderType(ociServiceTypeName)) {
            ociServiceTypeName = ociServiceTypeName.typeArguments().get(0);
        }
        assert (ociServiceTypeName.name().startsWith(OCI_PACKAGE_NAME_PREFIX)) : ociServiceTypeName.name();
        String body = toBody(ociServiceTypeName);
        Filer filer = proccessingEnv.getFiler();
    }

    private String toBody(TypeName ociServiceTypeName) {
        TemplateHelper templateHelper = TemplateHelper.create();
        String template = loadTemplate(TAG_TEMPLATE_SERVICE_PROVIDER_NAME);
        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", ociServiceTypeName.name());
        subst.put("simpleclassname", ociServiceTypeName.className());
        subst.put("packagename", ociServiceTypeName.packageName());
        return templateHelper.applySubstitutions(template, subst, true).trim();
    }

    private static String loadTemplate(String name) {
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
        if (GeneralProcessorUtils.findFirst(Inject.class, element.annotations()).isEmpty()) {
            return false;
        }

        if (TypeInfo.KIND_FIELD.equalsIgnoreCase(element.elementTypeKind())) {
            if (!shouldProcess(element.typeName(), processingEnv)) {
                return false;
            }
        } else if (TypeInfo.KIND_METHOD.equalsIgnoreCase(element.elementTypeKind())
                || TypeInfo.KIND_CONSTRUCTOR.equalsIgnoreCase(element.elementTypeKind())) {
            if (element.parameterArguments().stream().anyMatch(it -> shouldProcess(it.typeName(), processingEnv))) {
                return true;
            }
        }

        return false;
    }

    static boolean shouldProcess(TypeName typeName,
                                 ProcessingEnvironment processingEnv) {
        if (GeneralProcessorUtils.isProviderType(typeName) && typeName.typeArguments().size() > 0) {
            typeName = typeName.typeArguments().get(0);
        }

        if (!typeName.name().startsWith(OCI_PACKAGE_NAME_PREFIX)) {
            return false;
        }

        // check to see if we already generated it before
        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(toGeneratedClassName(typeName));
        return (typeElement == null);
    }

    private static CharSequence toGeneratedClassName(TypeName typeName) {
        return typeName.name() + "$$Pico$$Provider";
    }

}
