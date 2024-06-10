/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.runtime;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Activator;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.DependencyInfo;
import io.helidon.inject.api.ElementKind;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.api.ServiceInfoCriteria;

/**
 * This is the class the code-generator will target that will be used at runtime for a service provider to build up its
 * dependencies expressed as {@link DependenciesInfo}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class Dependencies {

    private Dependencies() {
    }

    /**
     * Creates a builder.
     *
     * @param serviceTypeName the service type name
     * @return the fluent builder
     */
    public static BuilderContinuation builder(TypeName serviceTypeName) {
        Objects.requireNonNull(serviceTypeName);
        return new BuilderContinuation(serviceTypeName);
    }

    /**
     * Creates a builder.
     *
     * @param serviceType the service type
     * @return the fluent builder
     */
    public static BuilderContinuation builder(Class<?> serviceType) {
        Objects.requireNonNull(serviceType);
        return builder(TypeName.create(serviceType));
    }

    /**
     * Combine the dependency info from the two sources to create a merged set of dependencies.
     *
     * @param parentDeps the parent set of dependencies
     * @param deps       the child set of dependencies
     * @return the combined set
     */
    public static DependenciesInfo combine(DependenciesInfo parentDeps,
                                           DependenciesInfo deps) {
        Objects.requireNonNull(parentDeps);
        Objects.requireNonNull(deps);

        DependenciesInfo.Builder builder = (deps instanceof DependenciesInfo.Builder)
                ? (DependenciesInfo.Builder) deps
                : DependenciesInfo.builder(deps);
        parentDeps.serviceInfoDependencies().forEach(builder::addServiceInfoDependencies);
        return builder.build();
    }

    static String toBaseIdentity(InjectionPointInfo.Builder dep) {
        ElementKind kind = dep.elementKind().orElseThrow();
        String elemName = dep.elementName().orElseThrow();
        AccessModifier access = dep.access().orElseThrow();
        String packageName = toPackageName(dep.serviceTypeName().orElseThrow());

        String baseId;
        if (ElementKind.FIELD == kind) {
            baseId = toFieldIdentity(elemName, packageName);
        } else {
            baseId = toMethodBaseIdentity(elemName,
                                          dep.elementArgs().orElseThrow(),
                                          access, packageName);
        }
        return baseId;
    }

    static String toId(InjectionPointInfo.Builder dep) {
        ElementKind kind = dep.elementKind().orElseThrow();
        String elemName = dep.elementName().orElseThrow();
        AccessModifier access = dep.access().orElseThrow();
        String packageName = toPackageName(dep.serviceTypeName().orElseThrow());

        String id;
        if (ElementKind.FIELD == kind) {
            id = toFieldIdentity(elemName, packageName);
        } else {
            id = toMethodIdentity(elemName,
                                  dep.elementArgs().orElseThrow(),
                                  dep.elementOffset().orElseThrow(() -> new IllegalStateException("Failed on " + elemName)),
                                  access,
                                  packageName);
        }
        return id;
    }

    /**
     * The field's identity and its base identity are the same since there is no arguments to handle.
     *
     * @param elemName    the non-null field name
     * @param packageName the package name of the owning service type containing the field
     * @return the field identity (relative to the owning service type)
     */
    public static String toFieldIdentity(String elemName,
                                         String packageName) {
        String id = Objects.requireNonNull(elemName);

        if (packageName != null) {
            id = packageName + "." + id;
        }
        return id;
    }

    /**
     * Computes the base identity given the method name and the number of arguments to the method.
     *
     * @param elemName       the method name
     * @param methodArgCount the number of arguments to the method
     * @param access         the method's access
     * @param packageName    the method's enclosing package name
     * @return the base identity (relative to the owning service type)
     */
    public static String toMethodBaseIdentity(String elemName,
                                              int methodArgCount,
                                              AccessModifier access,
                                              String packageName) {
        String id = Objects.requireNonNull(elemName) + "|" + methodArgCount;
        if (AccessModifier.PACKAGE_PRIVATE == access || elemName.equals(InjectionPointInfo.CONSTRUCTOR)) {
            if (packageName != null) {
                id = packageName + "." + id;
            }
        }
        return id;
    }

    /**
     * Computes the method's unique identity, taking into consideration the number of args it accepts
     * plus any optionally provided specific argument offset position.
     *
     * @param elemName       the method name
     * @param methodArgCount the number of arguments to the method
     * @param elemOffset     the optional parameter offset
     * @param access         the access for the method
     * @param packageName    the package name of the owning service type containing the method
     * @return the unique identity (relative to the owning service type)
     */
    public static String toMethodIdentity(String elemName,
                                          int methodArgCount,
                                          Integer elemOffset,
                                          AccessModifier access,
                                          String packageName) {
        String result = toMethodBaseIdentity(elemName, methodArgCount, access, packageName);

        if (elemOffset == null) {
            return result;
        }

        assert (elemOffset <= methodArgCount) : result;
        return result + "(" + elemOffset + ")";
    }

    private static String toPackageName(TypeName typeName) {
        String packageName = typeName.packageName();
        return packageName.isBlank() ? null : packageName;
    }

    /**
     * The continuation builder. This is a specialized builder used within the generated {@link Activator}.
     * It is specialized in that it validates and decorates over the normal builder, and provides a more streamlined interface.
     */
    public static class BuilderContinuation {
        private DependenciesInfo.Builder builder;
        private InjectionPointInfo.Builder ipInfoBuilder;

        private BuilderContinuation(TypeName serviceTypeName) {
            this.builder = DependenciesInfo.builder()
                    .fromServiceTypeName(serviceTypeName);
        }

        /**
         * Adds a new dependency item.
         *
         * @param elemName the element name
         * @param elemType the element type
         * @param kind     the element kind
         * @param access   the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(String elemName,
                                       Class<?> elemType,
                                       ElementKind kind,
                                       AccessModifier access) {
            if (ElementKind.FIELD != kind && Void.class != elemType) {
                throw new IllegalStateException("Should not use this for method element types");
            }
            TypeName fromServiceTypeName = builder.fromServiceTypeName().orElseThrow();
            return add(fromServiceTypeName, elemName, TypeName.create(elemType), kind, 0, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param elemName the element name
         * @param elemType the element type
         * @param kind     the element kind
         * @param elemArgs for methods, the number of arguments the method takes
         * @param access   the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(String elemName,
                                       Class<?> elemType,
                                       ElementKind kind,
                                       int elemArgs,
                                       AccessModifier access) {
            if (ElementKind.FIELD == kind && 0 != elemArgs) {
                throw new IllegalStateException("Should not have any arguments for field types: " + elemName);
            }
            TypeName fromServiceTypeName = builder.fromServiceTypeName().orElseThrow();
            return add(fromServiceTypeName, elemName, TypeName.create(elemType), kind, elemArgs, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param serviceType the service type
         * @param elemName    the element name
         * @param elemType    the element type
         * @param kind        the element kind
         * @param access      the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(Class<?> serviceType,
                                       String elemName,
                                       Class<?> elemType,
                                       ElementKind kind,
                                       AccessModifier access) {
            if (ElementKind.FIELD != kind) {
                throw new IllegalStateException("Should not use this for method element types");
            }
            return add(TypeName.create(serviceType), elemName, TypeName.create(elemType), kind, 0, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param serviceType the service type
         * @param elemName    the element name
         * @param elemType    the element type
         * @param kind        the element kind
         * @param elemArgs    used for methods only; the number of arguments the method accepts
         * @param access      the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(Class<?> serviceType,
                                       String elemName,
                                       Class<?> elemType,
                                       ElementKind kind,
                                       int elemArgs,
                                       AccessModifier access) {
            return add(TypeName.create(serviceType), elemName, TypeName.create(elemType), kind, elemArgs, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param ipInfo the injection point info already built
         * @return the builder
         */
        public BuilderContinuation add(InjectionPointInfo ipInfo) {
            commitLastDependency();

            ipInfoBuilder = InjectionPointInfo.builder(ipInfo);
            return this;
        }

        /**
         * Sets the element offset.
         *
         * @param offset the offset
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation elemOffset(int offset) {
            ipInfoBuilder.elementOffset(offset);
            return this;
        }

        /**
         * Sets the flag indicating the injection point is a list.
         *
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation listWrapped() {
            return listWrapped(true);
        }

        /**
         * Sets the flag indicating the injection point is a list.
         *
         * @param val true if list type
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation listWrapped(boolean val) {
            ipInfoBuilder.listWrapped(val);
            return this;
        }

        /**
         * Sets the flag indicating the injection point is a provider.
         *
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation providerWrapped() {
            return providerWrapped(true);
        }

        /**
         * Sets the flag indicating the injection point is a provider.
         *
         * @param val true if provider type
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation providerWrapped(boolean val) {
            ipInfoBuilder.providerWrapped(val);
            return this;
        }

        /**
         * Sets the flag indicating the injection point is an {@link java.util.Optional} type.
         *
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation optionalWrapped() {
            return optionalWrapped(true);
        }

        /**
         * Sets the flag indicating the injection point is an {@link java.util.Optional} type.
         *
         * @param val true if list type
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation optionalWrapped(boolean val) {
            ipInfoBuilder.optionalWrapped(val);
            return this;
        }

        /**
         * Sets the optional qualified name of the injection point.
         *
         * @param val the name
         * @return the builder
         */
        public BuilderContinuation named(String val) {
            ipInfoBuilder.addQualifier(Qualifier.createNamed(val));
            return this;
        }

        /**
         * Sets the optional qualifier of the injection point.
         *
         * @param val the qualifier
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation addQualifier(Class<? extends Annotation> val) {
            ipInfoBuilder.addQualifier(Qualifier.create(val));
            return this;
        }

        /**
         * Sets the optional qualifier of the injection point.
         *
         * @param val the qualifier
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation addQualifier(Qualifier val) {
            ipInfoBuilder.addQualifier(val);
            return this;
        }

        /**
         * Sets the optional qualifier of the injection point.
         *
         * @param val the qualifier
         * @return the builder
         */
        public BuilderContinuation qualifiers(Set<Qualifier> val) {
            ipInfoBuilder.qualifiers(val);
            return this;
        }

        /**
         * Sets the flag indicating that the injection point is static.
         *
         * @param val flag indicating if static
         * @return the builder
         */
        public BuilderContinuation staticDeclaration(boolean val) {
            ipInfoBuilder.staticDeclaration(val);
            return this;
        }

        /**
         * Name of the injection point code, such as argument or field.
         *
         * @param name name of the field or argument (if available)
         * @return the builder
         */
        public BuilderContinuation ipName(String name) {
            ipInfoBuilder.ipName(name);
            return this;
        }

        /**
         * Type of the injection point code, such as argument or field.
         *
         * @param type of the injection point, including all generic type arguments
         * @return the builder
         */
        public BuilderContinuation ipType(TypeName type) {
            ipInfoBuilder.ipType(type);
            return this;
        }

        /**
         * Commits the last dependency item, and prepares for the next.
         *
         * @return the builder
         */
        public DependenciesInfo build() {
            assert (builder != null);

            commitLastDependency();
            DependenciesInfo deps = builder.build();
            builder = null;
            return deps;
        }

        /**
         * Adds a new dependency item.
         *
         * @param serviceTypeName the service type
         * @param elemName        the element name
         * @param elemTypeName    the element type
         * @param kind            the element kind
         * @param elemArgs        used for methods only; this is the number of arguments the method accepts
         * @param access          the element access
         * @return the builder
         */
        public BuilderContinuation add(TypeName serviceTypeName,
                                       String elemName,
                                       TypeName elemTypeName,
                                       ElementKind kind,
                                       int elemArgs,
                                       AccessModifier access) {
            commitLastDependency();

            // thus begins a new builder continuation round
            ipInfoBuilder = InjectionPointInfo.builder()
                    .serviceTypeName(serviceTypeName)
                    .access(access)
                    .elementKind(kind)
                    .elementTypeName(elemTypeName)
                    .elementName(elemName)
                    .update(builder -> {
                        if (ElementKind.FIELD != kind) {
                            builder.elementOffset(0);
                        }
                    })
                    .elementArgs(elemArgs);
            return this;
        }

        /**
         * Commits the last dependency item to complete the last builder continuation.
         *
         * @return any built dependencies info realized from this last commit
         */
        // note: called from generated code
        public Optional<DependencyInfo> commitLastDependency() {
            String id = null;

            InjectionPointInfo.Builder previousBuilder = ipInfoBuilder;
            try {
                assert (builder != null);

                if (ipInfoBuilder != null) {
                    id = toId(ipInfoBuilder);
                    ipInfoBuilder.baseIdentity(toBaseIdentity(ipInfoBuilder));
                    ipInfoBuilder.id(id);
                    ServiceInfoCriteria criteria = ServiceInfoCriteria.builder()
                            .addContractImplemented(ipInfoBuilder.elementTypeName().orElseThrow())
                            .qualifiers(ipInfoBuilder.qualifiers())
                            .build();

                    InjectionPointInfo ipInfo = ipInfoBuilder
                            .dependencyToServiceInfo(criteria)
                            .build();
                    ipInfoBuilder = null;

                    DependencyInfo dep = DependencyInfo.builder()
                            .elementName(ipInfo.ipName())
                            .addInjectionPointDependency(ipInfo)
                            .dependencyTo(ipInfo.dependencyToServiceInfo())
                            .build();

                    builder.addServiceInfoDependency(ipInfo.dependencyToServiceInfo(), dep);
                    return Optional.of(dep);
                }

                return Optional.empty();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to commit a dependency for id: "
                                                        + id + ", failed builder: " + previousBuilder, e);
            }
        }
    }

}
