/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Provides classes and interfaces that integrate the
 * provider-independent parts of <a
 * href="https://javaee.github.io/tutorial/partpersist.html#BNBPY"
 * target="_parent">JPA</a> into CDI.
 *
 * @see io.helidon.integrations.cdi.jpa.JpaExtension
 *
 * @see io.helidon.integrations.cdi.jpa.PersistenceUnitInfoBean
 */
@Feature(value = "CDI/JPA", nativeDescription = "JPA Supports a limited set of use cases, please see http://...")
@Flavor(MP)
package io.helidon.integrations.cdi.jpa;

import io.helidon.common.Feature;
import io.helidon.common.Flavor;

import static io.helidon.common.HelidonFlavor.MP;