/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.sdk.cdi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

@FunctionalInterface
interface AdpSupplierSelector<K, T extends BasicAuthenticationDetailsProvider> {

    AdpSupplier<? extends T> select(K k);

    default List<AdpSupplier<? extends T>> select(Iterable<? extends K> i) {
        ArrayList<AdpSupplier<? extends T>> list = new ArrayList<>(9); // 9 == arbitrary, small
        i.forEach(k -> list.add(select(k)));
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

}
