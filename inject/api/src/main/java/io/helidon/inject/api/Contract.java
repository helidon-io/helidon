/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code Contract} annotation is used to relay significance to the type that it annotates. While remaining optional in its
 * use, it is typically placed on an interface definition to signify that the given type can be used for lookup in the
 * {@link Services} registry, and be eligible for injection via standard {@code @Inject}.
 * While normally placed on interface types, it can also be placed on abstract and concrete class as well. The main point is that
 * a {@code Contract} is the focal point for service lookup and injection.
 * <p>
 * If the developer does not have access to the source to place this annotation on the interface definition directly then consider
 * using {@link ExternalContracts} instead - this annotation can be placed on the implementation class implementing the given
 * {@code Contract} interface(s).
 *
 * @see ServiceInfo#contractsImplemented()
 * @see ServiceInfo#externalContractsImplemented()
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(java.lang.annotation.ElementType.TYPE)
public @interface Contract {

}
