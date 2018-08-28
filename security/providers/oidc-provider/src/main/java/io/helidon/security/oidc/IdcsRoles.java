/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.oidc;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.security.Grant;
import io.helidon.security.Role;
import io.helidon.security.Subject;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.providers.EvictableCache;

/**
 * Utility to obtain roles from IDCS server for a user.
 */
class IdcsRoles implements SubjectEnhancer {
    private static final Logger LOGGER = Logger.getLogger(IdcsRoles.class.getName());
    private static final String ACCESS_TOKEN_KEY = "access_token";

    private final OidcConfig config;
    private final EvictableCache<String, List<? extends Grant>> roleCache;

    // caching application token (as that can be re-used for group requests)
    private volatile SignedJwt appToken;
    private volatile Jwt appJwt;

    IdcsRoles(OidcConfig config) {
        this.config = config;
        // todo maybe allow control of timeouts?
        this.roleCache = EvictableCache.create();
    }

    @Override
    public void enhance(Jwt currentUser, Subject.Builder subjectBuilder) {
        getGrants(currentUser).forEach(subjectBuilder::addGrant);
    }

    private List<? extends Grant> getGrants(Jwt identityToken) {
        return identityToken.getSubject()
                // we have a subject, let's find the grants in cache, or from server
                .map(subject -> roleCache.computeValue(subject, () -> getGrantsFromServer(subject))
                        // we do not have a subject, no roles
                        .orElse(CollectionsHelper.listOf())).orElse(CollectionsHelper.listOf());

    }

    private Optional<List<? extends Grant>> getGrantsFromServer(String subject) {
        return getAppToken().flatMap(appToken -> {
            JsonObjectBuilder requestBuilder = Json.createObjectBuilder();
            requestBuilder.add("mappingAttributeValue", subject);
            requestBuilder.add("includeMemberships", true);
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            arrayBuilder.add("urn:ietf:params:scim:schemas:oracle:idcs:Asserter");
            requestBuilder.add("schemas", arrayBuilder);

            Response groupResponse = config.generalClient()
                    .target(config.identityUri() + "/admin/v1/Asserter")
                    .request()
                    .header("Authorization", "Bearer " + appToken)
                    .post(Entity.json(requestBuilder.build()));

            if (groupResponse.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                JsonObject jsonObject = groupResponse.readEntity(JsonObject.class);
                JsonArray groups = jsonObject.getJsonArray("groups");

                List<Role> result = new LinkedList<>();

                for (int i = 0; i < groups.size(); i++) {
                    JsonObject groupJson = groups.getJsonObject(i);
                    String groupName = groupJson.getString("display");
                    String groupId = groupJson.getString("value");
                    String groupRef = groupJson.getString("$ref");

                    Role role = Role.builder()
                            .name(groupName)
                            .addAttribute("groupId", groupId)
                            .addAttribute("groupRef", groupRef)
                            .build();

                    result.add(role);
                }

                return Optional.of(result);
            } else {
                LOGGER.warning("Cannot read groups for user \""
                                       + subject
                                       + "\". Response code: "
                                       + groupResponse.getStatus()
                                       + ", entity: "
                                       + groupResponse.readEntity(String.class));
                return Optional.empty();
            }
        });
    }

    private synchronized Optional<String> getAppToken() {
        // if cached and valid, use the cached token
        return OptionalHelper.from(getCachedAppToken())
                // otherwise retrieve a new one (and cache it as a side effect)
                .or(this::getAndCacheAppTokenFromServer)
                .asOptional()
                // we are interested in the text content of the token
                .map(SignedJwt::getTokenContent);
    }

    private Optional<SignedJwt> getCachedAppToken() {
        if (null == appToken) {
            return Optional.empty();
        }

        if (appJwt.validate(Jwt.defaultTimeValidators()).isValid()) {
            return Optional.of(appToken);
        }

        appToken = null;
        appJwt = null;

        return Optional.empty();
    }

    private Optional<SignedJwt> getAndCacheAppTokenFromServer() {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.putSingle("grant_type", "client_credentials");
        formData.putSingle("scope", "urn:opc:idm:__myscopes__");

        Response tokenResponse = config.tokenEndpoint()
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(formData));

        if (tokenResponse.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            JsonObject response = tokenResponse.readEntity(JsonObject.class);
            String accessToken = response.getString(ACCESS_TOKEN_KEY);
            LOGGER.finest(() -> "Access token: " + accessToken);
            SignedJwt signedJwt = SignedJwt.parseToken(accessToken);

            this.appToken = signedJwt;
            this.appJwt = signedJwt.getJwt();

            return Optional.of(signedJwt);
        } else {
            LOGGER.severe("Failed to obtain access token for application to read groups"
                                  + " from IDCS. Response code: " + tokenResponse.getStatus() + ", entity: "
                                  + tokenResponse.readEntity(String.class));
            return Optional.empty();
        }
    }
}
