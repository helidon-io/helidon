/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.microprofile.grpc.metrics;


import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.microprofile.metrics.MetricsCdiExtension;

import org.eclipse.microprofile.metrics.annotation.Metric;

/**
 * Generates test beans for metrics annotation coverage testing.
 * <p>
 *     The gRPC CDI extension removes metrics annotations from methods that also have {@code @GrpcMethod}. It does so
 *     by observing beans using {@code @WithAnnotations} that lists the metrics annotations. That list--as well as some
 *     other code in gRPC metrics--should be updated when new metrics appear in MP metrics.
 * </p>
 * <p>
 *     This annotation processor does not really process annotations but generates source code for test beans, one per metrics
 *     annotation (as recorded in the microprofile/metrics artifact). The test CDI extension then checks the generated beans
 *     to make sure the real gRPC extension has properly processed all metrics annotations.
 * </p>
 * <p>
 *     This class also generates a catalog of the generated bean classes. The test CDI extension loads that class as a
 *     service to find out exactly what classes were generated.
 * </p>
 */
@SupportedAnnotationTypes(value = {"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class CoverageTestBeanAP extends AbstractProcessor {

    private static final String PACKAGE = CoverageTestBeanAP.class.getPackageName();

    private static final List<String> TEST_BEAN_LINE_TEMPLATES = List.of(
            "package %1$s;",
            "import %2$s;",
            "import io.helidon.microprofile.grpc.core.GrpcMethod;",
            "import javax.enterprise.context.Dependent;",
            "@Dependent",
            "public class %4$s extends CoverageTestBeanBase {",
            "    @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)",
            "    @%3$s(name = \"coverage%3$s\")",
            "    public void measuredMethod() { }",
            "}");

    private static final List<String> CATALOG_LINE_TEMPLATES = List.of(
            "package %1$s;",
            "import java.util.List;",
            "import javax.enterprise.context.ApplicationScoped;",
            "@ApplicationScoped",
            "public class CoverageTestBeanCatalog implements TestMetricsCoverage.GeneratedBeanCatalog {",
            "    @Override",
            "    public List<Class<? extends CoverageTestBeanBase>> generatedBeanClasses() {",
            "        return List.of(%2$s);",
            "    }",
            "}");

    private boolean codeGenerationDone = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        /*
         * We will be run once per round of annotation processing, but we only generate classes during the first round.
         */
        if (!codeGenerationDone) {

            Set<String> generatedSimpleClassNames = generateTestBeanClasses();
            generateTestBeanCatalog(generatedSimpleClassNames);

            codeGenerationDone = true;
        }
        return true;
    }

    private Set<String> generateTestBeanClasses() {
        Set<Class<? extends Annotation>> annotationsToTest = new HashSet<>(GrpcMetricsCdiExtension.METRICS_ANNOTATIONS_TO_CHECK);

        Set<String> generatedSimpleClassNames = new HashSet<>();

        for (Class<? extends Annotation> a : annotationsToTest) {
            String generatedClassName = "CoverageTestBean" + a.getSimpleName();
            generatedSimpleClassNames.add(generatedClassName + ".class");

            generateSourceFile(generatedClassName, TEST_BEAN_LINE_TEMPLATES,
                    PACKAGE, a.getName(), a.getSimpleName(), generatedClassName);
        }
        return generatedSimpleClassNames;
    }

    private void generateTestBeanCatalog(Set<String> generatedSimpleClassNames) {

        String classNamesList = generatedSimpleClassNames.stream().collect(Collectors.joining(","));
        generateSourceFile("CoverageTestBeanCatalog", CATALOG_LINE_TEMPLATES, PACKAGE, classNamesList);
    }

//    private void generateTestBeanCatalogResource(Set<String> generatedSimpleClassNames) {
//        Filer filer = processingEnv.getFiler();
//        try {
//            FileObject catalogFileObject = filer.createResource(StandardLocation.CLASS_OUTPUT,
//                    "io.helidon.microprofile.grpc.metrics",
//                    "generatedTestBeanCatalog");
//            PrintWriter pw = new PrintWriter(catalogFileObject.openWriter());
//            generatedSimpleClassNames.forEach(n -> pw.println(String.format("%s.%s", PACKAGE, n)));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    private void generateSourceFile(String simpleClassName, List<String> formatLines, String... formatArgs) {
        Filer filer = processingEnv.getFiler();
        Messager messager = processingEnv.getMessager();

        try {
            JavaFileObject catalogFileObject =
                    filer.createSourceFile(outputFileName(simpleClassName));
            try (PrintWriter pw = new PrintWriter(catalogFileObject.openWriter())) {
                formatLines.forEach(t -> pw.println(String.format(t, formatArgs)));
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.WARNING, String.format("Error generating file %s;%n%s",
                    simpleClassName, e.toString()));
        }
    }

    private static String outputFileName(String simpleClassName) {
        return String.format("io.helidon.microprofile.grpc.metrics/%s.%s", PACKAGE, simpleClassName);
    }
}
