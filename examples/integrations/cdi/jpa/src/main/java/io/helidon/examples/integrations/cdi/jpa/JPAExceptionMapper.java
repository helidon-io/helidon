/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.cdi.jpa;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * An {@link ExceptionMapper} that handles {@link
 * PersistenceException}s.
 *
 * @see ExceptionMapper
 */
@ApplicationScoped
@Provider
public class JPAExceptionMapper implements ExceptionMapper<PersistenceException> {

  /**
   * Creates a new {@link JPAExceptionMapper}.
   */
  public JPAExceptionMapper() {
    super();
  }

  /**
   * Returns an appropriate non-{@code null} {@link Response} for the
   * supplied {@link PersistenceException}.
   *
   * @param persistenceException the {@link PersistenceException} that
   * caused this {@link JPAExceptionMapper} to be invoked; may be
   * {@code null}
   *
   * @return a non-{@code null} {@link Response} representing the
   * error
   */
  @Override
  public Response toResponse(final PersistenceException persistenceException) {
    final Response returnValue;
    if (persistenceException instanceof NoResultException
        || persistenceException instanceof EntityNotFoundException) {
      returnValue = Response.status(404).build();
    } else {
      returnValue = null;
      throw persistenceException;
    }
    return returnValue;
  }

}
