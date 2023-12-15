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

package io.helidon.integrations.oci.sdk.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.Option;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.codegen.InjectionCodegenContext;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.streaming.Stream;
import com.oracle.bmc.streaming.StreamAdmin;
import com.oracle.bmc.streaming.StreamAsync;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class OciInjectionProcessorObserverTest {
    private static OciInjectionCodegenObserver observer;

    @BeforeAll
    static void initTest() {
        observer = new OciInjectionCodegenObserver(new TestCodegenContext());
    }

    @Test
    void generatedInjectionArtifactsForTypicalOciServices() throws IOException {
        TypeName ociServiceType = TypeName.create(ObjectStorage.class);

        TypeName generatedOciServiceClientTypeName =
                OciInjectionCodegenObserver.toGeneratedServiceClientTypeName(ociServiceType);
        assertThat(generatedOciServiceClientTypeName.name(),
                   equalTo("io.helidon.integrations.generated." + ociServiceType.name() + "__Oci_Client"));

        ClassModel classModel = observer.toBody(ociServiceType,
                                                generatedOciServiceClientTypeName)
                .build();
        StringWriter sw = new StringWriter();
        classModel.write(sw);
        String stringBody = sw.toString();
        assertThat(stringBody.trim(),
                   equalTo(loadStringFromResource("expected/Objectstorage__Oci_Client._java_")));

        TypeName generatedOciServiceClientBuilderTypeName = OciInjectionCodegenObserver.toGeneratedServiceClientBuilderTypeName(
                ociServiceType);
        assertThat(generatedOciServiceClientBuilderTypeName.name(),
                   equalTo("io.helidon.integrations.generated." + ociServiceType.name() + "__Oci_ClientBuilder"));

        classModel = observer.toBuilderBody(ociServiceType,
                                            generatedOciServiceClientTypeName,
                                            generatedOciServiceClientBuilderTypeName)
                .build();
        sw = new StringWriter();
        classModel.write(sw);
        stringBody = sw.toString();
        assertThat(stringBody.trim(),
                   equalTo(loadStringFromResource("expected/Objectstorage__Oci_ClientBuilder._java_")));
    }

    @Test
    void oddballServiceTypeNames() {
        TypeName ociServiceType = TypeName.create(Stream.class);
        assertThat(observer.maybeDot(ociServiceType),
                   equalTo(""));
        assertThat(observer.usesRegion(ociServiceType),
                   equalTo(false));

        ociServiceType = TypeName.create(StreamAsync.class);
        assertThat(observer.maybeDot(ociServiceType),
                   equalTo(""));
        assertThat(observer.usesRegion(ociServiceType),
                   equalTo(false));

        ociServiceType = TypeName.create(StreamAdmin.class);
        assertThat(observer.maybeDot(ociServiceType),
                   equalTo("."));
        assertThat(observer.usesRegion(ociServiceType),
                   equalTo(true));
    }

    @Test
    void testShouldProcess() {
        TypeName typeName = TypeName.create(ObjectStorage.class);
        assertThat(observer.shouldProcess(typeName),
                   is(true));

        typeName = TypeName.create("com.oracle.bmc.circuitbreaker.OciCircuitBreaker");
        assertThat(observer.shouldProcess(typeName),
                   is(false));

        typeName = TypeName.create("com.oracle.another.Service");
        assertThat(observer.shouldProcess(typeName),
                   is(false));

        typeName = TypeName.create("com.oracle.bmc.Service");
        assertThat(observer.shouldProcess(typeName),
                   is(true));

        typeName = TypeName.create("com.oracle.bmc.ServiceClient");
        assertThat(observer.shouldProcess(typeName),
                   is(false));

        typeName = TypeName.create("com.oracle.bmc.ServiceClientBuilder");
        assertThat(observer.shouldProcess(typeName),
                   is(false));
    }

    @Test
    void loadTypeNameExceptions() {
        Map<String, String> options = Map.of(OciInjectCodegenObserverProvider.OPTION_TYPENAME_EXCEPTIONS.name(),
                                             "M1, M2");
        OciInjectionCodegenObserver observer = new OciInjectionCodegenObserver(new TestCodegenContext(options));

        assertThat(observer.typenameExceptions(),
                   containsInAnyOrder("M1",
                                      "M2",
                                      "test1",
                                      "com.oracle.bmc.Region",
                                      "com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider",
                                      "com.oracle.bmc.circuitbreaker.OciCircuitBreaker"
                   ));
    }

    @Test
    void loadNoDotExceptions() {
        Map<String, String> options = Map.of(OciInjectCodegenObserverProvider.OPTION_NO_DOT_EXCEPTIONS.name(),
                                             "Manual1, Manual2 ");
        OciInjectionCodegenObserver observer = new OciInjectionCodegenObserver(new TestCodegenContext(options));

        assertThat(observer.noDotExceptions(),
                   containsInAnyOrder("Manual1",
                                      "Manual2",
                                      "test2",
                                      "com.oracle.bmc.streaming.Stream",
                                      "com.oracle.bmc.streaming.StreamAsync"
                   ));
    }

    private static String loadStringFromResource(String resourceNamePath) {
        try {
            try (InputStream in = OciInjectionProcessorObserverTest.class.getClassLoader()
                    .getResourceAsStream(resourceNamePath)) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                return result.replaceAll("\\{\\{YEAR}}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)))
                        .trim(); // remove leading and trailing whitespaces
            }
        } catch (Exception e) {
            throw new CodegenException("Failed to load: " + resourceNamePath, e);
        }
    }

    private static class TestCodegenContext implements InjectionCodegenContext {
        private final Map<String, String> options;

        TestCodegenContext() {
            this(Map.of());
        }

        TestCodegenContext(Map<String, String> options) {
            this.options = options;
        }

        @Override
        public Optional<ModuleInfo> module() {
            return Optional.empty();
        }

        @Override
        public CodegenFiler filer() {
            return null;
        }

        @Override
        public CodegenLogger logger() {
            return null;
        }

        @Override
        public CodegenScope scope() {
            return null;
        }

        @Override
        public CodegenOptions options() {
            return option -> Optional.ofNullable(options.get(option)).map(String::trim);
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName) {
            return Optional.empty();
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
            return Optional.empty();
        }

        @Override
        public List<ElementMapper> elementMappers() {
            return null;
        }

        @Override
        public List<TypeMapper> typeMappers() {
            return null;
        }

        @Override
        public List<AnnotationMapper> annotationMappers() {
            return null;
        }

        @Override
        public Set<TypeName> mapperSupportedAnnotations() {
            return null;
        }

        @Override
        public Set<String> mapperSupportedAnnotationPackages() {
            return null;
        }

        @Override
        public Set<Option<?>> supportedOptions() {
            return null;
        }

        @Override
        public Optional<ClassModel.Builder> descriptor(TypeName serviceType) {
            return Optional.empty();
        }

        @Override
        public void addDescriptor(TypeName serviceType,
                                  TypeName descriptorType,
                                  ClassModel.Builder descriptor,
                                  Object... originatingElements) {

        }

        @Override
        public void addType(TypeName type, ClassModel.Builder newClass, TypeName mainTrigger, Object... originatingElements) {

        }

        @Override
        public Optional<ClassModel.Builder> type(TypeName type) {
            return Optional.empty();
        }

        @Override
        public TypeName descriptorType(TypeName serviceType) {
            return null;
        }

        @Override
        public List<ClassCode> types() {
            return null;
        }

        @Override
        public List<ClassCode> descriptors() {
            return null;
        }

        @Override
        public boolean isProvider(TypeName typeName) {
            return false;
        }

        @Override
        public Assignment assignment(TypeName typeName, String valueSource) {
            return new Assignment(typeName, it -> it.addContent(valueSource));
        }
    }
}
