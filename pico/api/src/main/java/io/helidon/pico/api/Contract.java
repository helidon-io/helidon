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

package io.helidon.pico.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Contract annotation is used to relay significance to the type. While remaining optional in its use, it is typically placed
 * on an interface definition to signify that the given type can be used for lookup in the service registry, or otherwise be
 * eligible for injection via standard @Inject. It could also be used on other types (e.g., abstract class) as well.
 * <p>
 *
 * If the developer does not have access to the source to place this annotation on the interface definition then consider using
 * {@link ExternalContracts} instead - this annotation can be placed on the implementation class implementing the given interface.
 *
 * See io.helidon.pico.spi.ServiceInfo#getContractsImplemented()
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.TYPE)
public @interface Contract {

}
