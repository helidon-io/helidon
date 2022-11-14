/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.types;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.impl.ReflectionHandler;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.types.testsubjects.MultiValuedAnnotation;
import io.helidon.pico.tools.types.testsubjects.SingleValueAnno;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ScanResult;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.types.DefaultTypeName.create;
import static io.helidon.pico.tools.processor.TypeTools.componentTypeNameOf;
import static io.helidon.pico.tools.processor.TypeTools.extractInjectionPointTypeInfo;
import static io.helidon.pico.tools.processor.TypeTools.providesContractType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SingleValueAnno
@Named("name")
@MultiValuedAnnotation(value = "str1", someOtherStringValue = "str2", intValue = 1, booleanValue = true)
public class TypeToolsTest {
    ScanResult scan = ReflectionHandler.INSTANCE.getScan();

    ClassInfo testClassInfo = Objects.requireNonNull(
            scan.getClassInfo(TypeToolsTest.class.getName()));
    ClassInfo providerOfGenericClassInfo = Objects.requireNonNull(
            scan.getClassInfo(ProviderOfGeneric.class.getName()));
    ClassInfo providerOfDirectClassInfo = Objects.requireNonNull(
            scan.getClassInfo(ProviderOfTypeDirect.class.getName()));
    ClassInfo providerOfTypeThroughSuperClassInfo = Objects.requireNonNull(
            scan.getClassInfo(ProviderOfTypeThroughSuper.class.getName()));
    ClassInfo injectionPointProviderOfTypeDirectClassInfo = Objects.requireNonNull(
            scan.getClassInfo(InjectionPointProviderOfTypeDirect.class.getName()));

    String typeToolsTestTypeName = create(TypeToolsTest.class).name();
    String stringTypeName = create(String.class).name();
//    String integerTypeName = create(Integer.class).getName();
    String booleanTypeName = create(Boolean.class).name();

    @Test
    public void componentNameOf() {
        assertEquals("Whatever", componentTypeNameOf("jakarta.inject.Provider<Whatever>"));
        assertEquals("Whatever", componentTypeNameOf("Optional<Whatever>"));
        assertEquals("Whatever", componentTypeNameOf("Whatever"));
    }

    @Test
    public void testExtractInjectionPointTypeInfo() {
        assertNull(providesContractType(testClassInfo));
        ToolsException te = assertThrows(ToolsException.class, () -> providesContractType(providerOfGenericClassInfo));
        assertEquals("unsupported provider<> type of D in abstract static class io.helidon.pico.tools.types"
                        + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                     te.getMessage());
        assertEquals(stringTypeName, providesContractType(providerOfDirectClassInfo));
        // we should eventually support this next case
        te = assertThrows(ToolsException.class, () -> providesContractType(providerOfTypeThroughSuperClassInfo));
        assertEquals("unsupported provider<> type of D in static class io.helidon.pico.tools.types"
                        + ".TypeToolsTest$ProviderOfTypeThroughSuper extends io.helidon.pico.tools.types"
                        + ".TypeToolsTest$ProviderOfGeneric<java.lang.Integer>",
                     te.getMessage());
        assertEquals(booleanTypeName, providesContractType(injectionPointProviderOfTypeDirectClassInfo));
    }

    @Test
    public void optionalsProvidersAndListsOfFieldInfo() {
        optionalsProvidersAndLists(typeToolsTestTypeName, true, true, false,
                                   providerOfGenericClassInfo.getFieldInfo("listOfProviders"));
        optionalsProvidersAndLists(typeToolsTestTypeName, true, false, true,
                                   providerOfGenericClassInfo.getFieldInfo("optionalProvider"));
        optionalsProvidersAndLists(typeToolsTestTypeName, false, false, true,
                                   providerOfGenericClassInfo.getFieldInfo("optionalNotProvider"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools.types"
                        + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                                            providerOfGenericClassInfo.getFieldInfo("generic"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools.types"
                                                    + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                                            providerOfGenericClassInfo.getFieldInfo("listOfProvidersOfGeneric"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools.types"
                                                    + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                                            providerOfGenericClassInfo.getFieldInfo("optionalProviderOfGeneric"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools.types"
                                                    + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                                            providerOfGenericClassInfo.getFieldInfo("optionalOfGeneric"));

        optionalsProvidersAndLists(stringTypeName, true, false, false,
                                   providerOfDirectClassInfo.getFieldInfo("providerOfString"));
        optionalsProvidersAndLists(stringTypeName, true, true, false,
                                   providerOfDirectClassInfo.getFieldInfo("listOfProvidersOfStrings"));
        optionalsProvidersAndLists(stringTypeName, true, false, true,
                                   providerOfDirectClassInfo.getFieldInfo("optionalProviderOfString"));
        optionalsProvidersAndLists(stringTypeName, false, false, true,
                                   providerOfDirectClassInfo.getFieldInfo("optionalString"));
        optionalsProvidersAndLists(stringTypeName, false, false, false,
                                   providerOfDirectClassInfo.getFieldInfo("string"));

        optionalsProvidersAndLists(stringTypeName, true, false, false,
                                   providerOfDirectClassInfo.getFieldInfo("providerOfString"));
        optionalsProvidersAndLists(stringTypeName, true, true, false,
                                   providerOfDirectClassInfo.getFieldInfo("listOfProvidersOfStrings"));
        optionalsProvidersAndLists(stringTypeName, true, false, true,
                                   providerOfDirectClassInfo.getFieldInfo("optionalProviderOfString"));
        optionalsProvidersAndLists(stringTypeName, false, false, true,
                                   providerOfDirectClassInfo.getFieldInfo("optionalString"));
        optionalsProvidersAndLists(stringTypeName, false, false, false,
                                   providerOfDirectClassInfo.getFieldInfo("string"));
    }

    @Test
    public void optionalsProvidersAndListsOfMethodParams() {
        optionalsProvidersAndLists(typeToolsTestTypeName, true, false, false,
                                   providerOfGenericClassInfo.getMethodInfo("setProvider")
                                           .get(0).getParameterInfo()[0]);
        optionalsProvidersAndLists(typeToolsTestTypeName, true, true, false,
                                   providerOfGenericClassInfo.getMethodInfo("setListOfProviders")
                                           .get(0).getParameterInfo()[0]);
        optionalsProvidersAndLists(typeToolsTestTypeName, true, false, true,
                                   providerOfGenericClassInfo.getMethodInfo("setOptionalProvider")
                                           .get(0).getParameterInfo()[0]);
        optionalsProvidersAndLists(typeToolsTestTypeName, false, false, true,
                                   providerOfGenericClassInfo.getMethodInfo("setOptionalNotProvider")
                                           .get(0).getParameterInfo()[0]);
        optionalsProvidersAndLists(typeToolsTestTypeName, false, false, false,
                                   providerOfGenericClassInfo.getMethodInfo("setTyped")
                                           .get(0).getParameterInfo()[0]);
        optionalsProvidersAndListsException("unsupported type for D in public void setGeneric(D)",
                                            providerOfGenericClassInfo.getMethodInfo("setGeneric")
                                                    .get(0).getParameterInfo()[0]);

        optionalsProvidersAndLists(stringTypeName, true, false, false,
                                   providerOfDirectClassInfo.getMethodInfo("setProviderOfString")
                                           .get(0).getParameterInfo()[0]);
        optionalsProvidersAndLists(stringTypeName, false, false, false,
                                   providerOfDirectClassInfo.getMethodInfo("setString")
                                           .get(0).getParameterInfo()[0]);
    }

    @Test
    public void annotationCreateMultiValued() {
        MultiValuedAnnotation annotation = getClass().getAnnotation(MultiValuedAnnotation.class);
        assertNotNull(annotation);
        AnnotationAndValue annotationAndValue = TypeTools.createAnnotationAndValueFromAnnotation(annotation);
        assertEquals(MultiValuedAnnotation.class.getName(), annotationAndValue.typeName().name());
        assertEquals("str1", annotationAndValue.value().orElse(null));
        assertEquals("str1", annotationAndValue.value("value").orElse(null));
        assertEquals(String.valueOf(1), annotationAndValue.value("intValue").orElse(null));
        assertEquals(String.valueOf(Boolean.TRUE), annotationAndValue.value("booleanValue").orElse(null));
    }

    @Test
    public void annotationTestEquals() {
        AnnotationAndValue val1 = DefaultAnnotationAndValue.create(Named.class, "name");
        AnnotationAndValue val2 = TypeTools.createAnnotationAndValueFromAnnotation(getClass().getAnnotation(Named.class));
        assertEquals(val1, val2);
        assertEquals(val2, val1);

        val1 = TypeTools.createAnnotationAndValueFromAnnotation(getClass().getAnnotation(Named.class));
        assertEquals(val1, val2);
        assertEquals(val2, val1);
    }

    @Test
    public void qualifierToQualifierAndValue() {
        QualifierAndValue val = DefaultQualifierAndValue.create(Named.class, "name");
        assertEquals("DefaultQualifierAndValue(typeName=jakarta.inject.Named, value=name)", val.toString());

        QualifierAndValue val2 = TypeTools.createQualifierAndValue(getClass().getAnnotation(SingleValueAnno.class));
        assertEquals("DefaultQualifierAndValue(typeName=io.helidon.pico.tools.types.testsubjects.SingleValueAnno, values={value=})",
                     val2.toString());
    }

    @Test
    public void qualifierTestEquals() {
        QualifierAndValue val1 = DefaultQualifierAndValue.create(Named.class, "name");
        AnnotationAndValue val2 = DefaultAnnotationAndValue.create(DefaultTypeName.create(Named.class),
                                                                   Collections.singletonMap("value", "name"));
        assertEquals(val1, val2);
        assertEquals(val2, val1);

        val2 = DefaultQualifierAndValue.createNamed("name");
        assertEquals(val1, val2);
        assertEquals(val2, val1);

        val2 = TypeTools.createQualifierAndValue(getClass().getAnnotation(Named.class));
        assertEquals(val1, val2);
        assertEquals(val2, val1);

        val1 = TypeTools.createQualifierAndValue(getClass().getAnnotation(SingleValueAnno.class));
        val2 = DefaultAnnotationAndValue.create(SingleValueAnno.class);
        assertEquals(val1, val2);
        assertEquals(val2, val1);
    }

    @Test
    public void qualifierContainsAll() {
        Set<QualifierAndValue> serviceInfo = Set.of(DefaultQualifierAndValue.createNamed("name"),
                                                    TypeTools.createQualifierAndValue(getClass().getAnnotation(SingleValueAnno.class)));
        Set<QualifierAndValue> criteria = Set.of(DefaultQualifierAndValue.create(Named.class, "name"));
        assertTrue(serviceInfo.containsAll(criteria));
        criteria = Set.of(DefaultQualifierAndValue.create(SingleValueAnno.class));
        assertTrue(serviceInfo.containsAll(criteria));
    }

    private void optionalsProvidersAndLists(String expectedType,
                                            boolean expectedProvider,
                                            boolean expectedList,
                                            boolean expectedOptional,
                                            FieldInfo fld) {
        assertNotNull(fld);
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        assertEquals(expectedType, extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional), fld.toString());
        assertEquals(expectedProvider, isProvider.get(), "provider for " + fld);
        assertEquals(expectedList, isList.get(), "list for " + fld);
        assertEquals(expectedOptional, isOptional.get(), "optional for " + fld);
    }

    private void optionalsProvidersAndListsException(String exceptedException,
                                                     FieldInfo fld) {
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        ToolsException te = assertThrows(ToolsException.class,
                                         () -> extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional));
        assertEquals(exceptedException, te.getMessage(), fld.toString());
    }

    private void optionalsProvidersAndLists(String expectedType,
                                            boolean expectedProvider,
                                            boolean expectedList,
                                            boolean expectedOptional,
                                            MethodParameterInfo fld) {
        assertNotNull(fld);
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        assertEquals(expectedType, extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional), fld.toString());
        assertEquals(expectedProvider, isProvider.get(), "provider for " + fld);
        assertEquals(expectedList, isList.get(), "list for " + fld);
        assertEquals(expectedOptional, isOptional.get(), "optional for " + fld);
    }

    private void optionalsProvidersAndListsException(String exceptedException,
                                                     MethodParameterInfo fld) {
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        ToolsException te = assertThrows(ToolsException.class,
                                         () -> extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional));
        assertEquals(exceptedException, te.getMessage(), fld.toString());
    }

    static abstract class ProviderOfGeneric<D> implements Provider<D> {
        List<Provider<TypeToolsTest>> listOfProviders;
        Optional<Provider<TypeToolsTest>> optionalProvider;
        Optional<TypeToolsTest> optionalNotProvider;
        D generic;
        List<Provider<D>> listOfProvidersOfGeneric;
        Optional<Provider<D>> optionalProviderOfGeneric;
        Optional<D> optionalOfGeneric;

        public void setProvider(Provider<TypeToolsTest> ignored) {
        }

        public void setListOfProviders(List<Provider<TypeToolsTest>> ignored) {
        }

        public void setOptionalProvider(Optional<Provider<TypeToolsTest>> ignored) {
        }

        public void setOptionalNotProvider(Optional<TypeToolsTest> ignored) {
        }

        public void setTyped(TypeToolsTest ignored) {
        }

        public void setGeneric(D ignored) {
        }
    }

    static class ProviderOfTypeDirect implements Provider<String> {
        Provider<String> providerOfString;
        List<Provider<String>> listOfProvidersOfStrings;
        Optional<Provider<String>> optionalProviderOfString;
        Optional<String> optionalString;
        String string;

        public ProviderOfTypeDirect(Provider<String> provider) {
            this.providerOfString = provider;
        }

        @Override
        public String get() {
            return Objects.isNull(providerOfString) ? null : providerOfString.get();
        }

        void setProviderOfString(Provider<String> provider) {
            this.providerOfString = provider;
        }

        void setString(String ignored) {
        }
    }

    static class ProviderOfTypeThroughSuper extends ProviderOfGeneric<Integer> {
        Provider<Integer> provider;

        public ProviderOfTypeThroughSuper(Provider<Integer> provider) {
            this.provider = provider;
        }

        @Override
        public Integer get() {
            return Objects.isNull(provider) ? null : provider.get();
        }
    }

    static class InjectionPointProviderOfTypeDirect
            extends ProviderOfGeneric<Boolean> implements InjectionPointProvider<Boolean> {
        Provider<Boolean> provider;

        public InjectionPointProviderOfTypeDirect(Provider<Boolean> provider) {
            this.provider = provider;
        }

        @Override
        public Boolean get() {
            return Objects.isNull(provider) ? null : provider.get();
        }

        @Override
        public Boolean get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
            return get();
        }
    }

}
