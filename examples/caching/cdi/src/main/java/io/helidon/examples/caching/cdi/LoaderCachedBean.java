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
 */

package io.helidon.examples.caching.cdi;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.caching.annotation.CacheAdd;
import io.helidon.caching.annotation.CacheGet;
import io.helidon.caching.annotation.CacheKey;
import io.helidon.caching.annotation.CacheName;
import io.helidon.caching.annotation.CachePut;
import io.helidon.caching.annotation.CacheRemove;
import io.helidon.caching.annotation.CacheValue;

@ApplicationScoped
@CacheName("loader-cache")
public class LoaderCachedBean {

    @CacheGet
    public Integer getIt(@CacheKey Integer id) {
        // this should never be called
        return id;
    }

    @CacheAdd
    public Integer createIt(@CacheKey Integer id) {
        return id;
    }

    @CachePut
    public void updateIt(@CacheKey Integer id, @CacheValue Integer value) {

    }

    @CacheRemove
    public void remove(@CacheKey Integer id) {

    }
}
