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
package io.helidon.integrations.examples.jedis.jaxrs;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import redis.clients.jedis.Jedis;

/**
 * A JAX-RS resource class rooted at {@code /jedis}.
 *
 * @see #get(String)
 *
 * @see #set(UriInfo, String, String)
 *
 * @see #del(String)
 */
@Path("/jedis")
@ApplicationScoped
public class RedisClientResource {

  private final Provider<Jedis> clientProvider;

  /**
   * Creates a new {@link RedisClientResource}.
   *
   * @param clientProvider a {@link Provider} of a {@link Jedis}
   * instance; must not be {@code null}
   *
   * @exception NullPointerException if {@code clientProvider} is
   * {@code null}
   */
  @Inject
  public RedisClientResource(final Provider<Jedis> clientProvider) {
    super();
    this.clientProvider = Objects.requireNonNull(clientProvider);
  }

  /**
   * Returns a non-{@code null} {@link Response} which, if successful,
   * will contain any value indexed under the supplied Redis key.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param key the key whose value should be deleted; must not be
   * {@code null}
   *
   * @return a non-{@code null} {@link Response}
   *
   * @see #set(UriInfo, String, String)
   *
   * @see #del(String)
   */
  @GET
  @Path("/{key}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response get(@PathParam("key") final String key) {
    final Response returnValue;
    if (key == null || key.isEmpty()) {
      returnValue = Response.status(400)
        .build();
    } else {
      final String response = this.clientProvider.get().get(key);
      if (response == null) {
        returnValue = Response.status(404)
          .build();
      } else {
        returnValue = Response.ok()
          .entity(response)
          .build();
      }
    }
    return returnValue;
  }

  /**
   * Sets a value under a key in a Redis system.
   *
   * @param uriInfo a {@link UriInfo} describing the current request;
   * must not be {@code null}
   *
   * @param key the key in question; must not be {@code null}
   *
   * @param value the value to set; may be {@code null}
   *
   * @return a non-{@code null} {@link Response} indicating the status
   * of the operation
   *
   * @exception NullPointerException if {@code uriInfo} is {@code
   * null}
   *
   * @see #del(String)
   */
  @PUT
  @Path("/{key}")
  @Consumes(MediaType.TEXT_PLAIN)
  public Response set(@Context final UriInfo uriInfo,
                      @PathParam("key") final String key,
                      final String value) {
    Objects.requireNonNull(uriInfo);
    final Response returnValue;
    if (key == null || key.isEmpty() || value == null) {
      returnValue = Response.status(400)
        .build();
    } else {
      final Object priorValue = this.clientProvider.get().getSet(key, value);
      if (priorValue == null) {
        returnValue = Response.created(uriInfo.getRequestUri())
          .build();
      } else {
        returnValue = Response.ok()
          .build();
      }
    }
    return returnValue;
  }

  /**
   * Deletes a value from Redis.
   *
   * @param key the key identifying the value to delete; must not be
   * {@code null}
   *
   * @return a non-{@code null} {@link Response} describing the result
   * of the operation
   *
   * @see #get(String)
   *
   * @see #set(UriInfo, String, String)
   */
  @DELETE
  @Path("/{key}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response del(@PathParam("key") final String key) {
    final Response returnValue;
    if (key == null || key.isEmpty()) {
      returnValue = Response.status(400)
        .build();
    } else {
      final Long numberOfKeysDeleted = this.clientProvider.get().del(key);
      if (numberOfKeysDeleted == null || numberOfKeysDeleted.longValue() <= 0L) {
        returnValue = Response.status(404)
          .build();
      } else {
        returnValue = Response.noContent()
          .build();
      }
    }
    return returnValue;
  }

}
