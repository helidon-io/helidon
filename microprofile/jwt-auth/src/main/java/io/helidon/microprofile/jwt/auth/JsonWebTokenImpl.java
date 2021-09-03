/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.jwt.auth;

import java.util.Collection;
import java.util.Set;

import io.helidon.security.Principal;
import io.helidon.security.jwt.SignedJwt;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Implementation of {@link JsonWebToken} with additional support of {@link io.helidon.security.util.AbacSupport}.
 *
 * @deprecated this class will not be public in future versions of Helidon
 */
// do not remove this class, just make it package private
@Deprecated(forRemoval = true, since = "2.4.0")
public class JsonWebTokenImpl implements JsonWebToken, Principal {
    protected JsonWebTokenImpl() {
    }

    static JsonWebTokenImpl empty() {
        return new JsonWebTokenImpl();
    }

    /**
     * Creates a new instance based on the signed JWT instance.
     *
     * @param signed singed JWT retrieved from request
     * @return a json web token.
     */
    static JsonWebTokenImpl create(SignedJwt signed) {
        return new BackedJsonWebToken(signed);
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<String> getClaimNames() {
        return null;
    }

    @Override
    public <T> T getClaim(String claimName) {
        return null;
    }

    @Override
    public String id() {
        return "anonymous";
    }

    @Override
    public Object abacAttributeRaw(String key) {
        return null;
    }

    @Override
    public Collection<String> abacAttributeNames() {
        return Set.of();
    }

    /**
     * Produce a claim based on its name and expected class.
     *
     * @param claimName name of the claim
     * @param clazz     expected type
     * @param <T>       type
     * @return claim value
     * @deprecated This class will no longer be public in future versions
     */
    @Deprecated(forRemoval = true, since = "2.4.0")
    public <T> T getClaim(String claimName, Class<T> clazz) {
        return null;
    }

}
