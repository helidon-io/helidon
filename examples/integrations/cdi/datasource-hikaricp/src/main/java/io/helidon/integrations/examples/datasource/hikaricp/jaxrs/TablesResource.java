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
package io.helidon.integrations.examples.datasource.hikaricp.jaxrs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * A JAX-RS resource class in {@linkplain ApplicationScoped
 * application scope} rooted at {@code /tables}.
 *
 * @see #get()
 */
@Path("/tables")
@ApplicationScoped
public class TablesResource {

  private final DataSource dataSource;

  /**
   * Creates a new {@link TablesResource}.
   *
   * @param dataSource the {@link DataSource} to use to acquire
   * database table names; must not be {@code null}
   *
   * @exception NullPointerException if {@code dataSource} is {@code
   * null}
   */
  @Inject
  public TablesResource(@Named("example") final DataSource dataSource) {
    super();
    this.dataSource = Objects.requireNonNull(dataSource);
  }

  /**
   * Returns a {@link Response} which, if successful, contains a
   * newline-separated list of Oracle database table names.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a non-{@code null} {@link Response}
   *
   * @exception SQLException if a database error occurs
   */
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public Response get() throws SQLException {
    final StringBuilder sb = new StringBuilder();
    try (Connection connection = this.dataSource.getConnection();
         PreparedStatement ps =
           connection.prepareStatement(" SELECT TABLE_NAME"
                                       + " FROM ALL_TABLES "
                                       + "ORDER BY TABLE_NAME ASC");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        sb.append(rs.getString(1)).append("\n");
      }
    }
    final Response returnValue = Response.ok()
      .entity(sb.toString())
      .build();
    return returnValue;
  }

}
