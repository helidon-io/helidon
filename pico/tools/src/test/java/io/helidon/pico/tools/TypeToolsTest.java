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

package io.helidon.pico.tools;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ScanResult;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;

import static io.helidon.common.types.DefaultTypeName.create;
import static io.helidon.pico.tools.TypeTools.extractInjectionPointTypeInfo;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Named("name")
public class TypeToolsTest {
    ScanResult scan = ReflectionHandler.INSTANCE.scan();

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
    String booleanTypeName = create(Boolean.class).name();

    @Test
    void optionalsProvidersAndListsOfFieldInfo() {
        optionalsProvidersAndLists(typeToolsTestTypeName, true, true, false,
                                   providerOfGenericClassInfo.getFieldInfo("listOfProviders"));
        optionalsProvidersAndLists(typeToolsTestTypeName, true, false, true,
                                   providerOfGenericClassInfo.getFieldInfo("optionalProvider"));
        optionalsProvidersAndLists(typeToolsTestTypeName, false, false, true,
                                   providerOfGenericClassInfo.getFieldInfo("optionalNotProvider"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools"
                        + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                                            providerOfGenericClassInfo.getFieldInfo("generic"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools"
                                                    + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                                            providerOfGenericClassInfo.getFieldInfo("listOfProvidersOfGeneric"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools"
                                                    + ".TypeToolsTest$ProviderOfGeneric<D> implements jakarta.inject.Provider<D>",
                                            providerOfGenericClassInfo.getFieldInfo("optionalProviderOfGeneric"));
        optionalsProvidersAndListsException("unsupported type for D in abstract static class io.helidon.pico.tools"
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
    void optionalsProvidersAndListsOfMethodParams() {
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

    private void optionalsProvidersAndLists(
            String expectedType,
            boolean expectedProvider,
            boolean expectedList,
            boolean expectedOptional,
            FieldInfo fld) {
        assertNotNull(fld);
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        assertThat(extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional), equalTo(expectedType));
        assertThat("provider for " + fld, isProvider.get(), is(expectedProvider));
        assertThat("list for " + fld, isList.get(), equalTo(expectedList));
        assertThat("optional for " + fld, isOptional.get(), is(expectedOptional));
    }

    private void optionalsProvidersAndListsException(
            String exceptedException,
            FieldInfo fld) {
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        ToolsException te = assertThrows(ToolsException.class,
                                         () -> extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional));
        assertThat(fld.toString(), te.getMessage(), equalTo(exceptedException));
    }

    private void optionalsProvidersAndLists(
            String expectedType,
            boolean expectedProvider,
            boolean expectedList,
            boolean expectedOptional,
            MethodParameterInfo fld) {
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        assertThat(extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional), equalTo(expectedType));
        assertThat("provider for " + fld, isProvider.get(), is(expectedProvider));
        assertThat("list for " + fld, isList.get(), equalTo(expectedList));
        assertThat("optional for " + fld, isOptional.get(), is(expectedOptional));
    }

    private void optionalsProvidersAndListsException(
            String exceptedException,
            MethodParameterInfo fld) {
        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        ToolsException te = assertThrows(ToolsException.class,
                                         () -> extractInjectionPointTypeInfo(fld, isProvider, isList, isOptional));
        assertThat(fld.toString(), te.getMessage(), equalTo(exceptedException));
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
            extends ProviderOfGeneric<Boolean> implements Provider<Boolean> {
        Provider<Boolean> provider;

        public InjectionPointProviderOfTypeDirect(Provider<Boolean> provider) {
            this.provider = provider;
        }

        @Override
        public Boolean get() {
            return Objects.isNull(provider) ? null : provider.get();
        }
    }

}
