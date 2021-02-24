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
package io.helidon.lra;

import oracle.ucp.jdbc.PoolDataSource;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


@ApplicationScoped
@Path("lra-recovery")
public class RecoveryManager  {
    private static final Logger LOGGER = Logger.getLogger(RecoveryManager.class.getName());

    @Inject
    @Named("coordinatordb")
    PoolDataSource coordinatordb;

    private long timeSinceLastPurge = System.currentTimeMillis();
    private static Connection connection = null;
    List<String> lraRecoveryRecordMap = new ArrayList<>();

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        LOGGER.info("RecoveryManager init coordinatordb:" + coordinatordb);
    }

    static void log(LRA lra, String compensatorLink){
        if(true) return;
        LOGGER.info("LRARecordPersistence.log... lraRecord.lraId = " + lra.lraId + ", compensatorLink = " + compensatorLink);
        try { //todo store the timeout
            connection.createStatement().execute(
                    "insert into lrarecords values ('"+ lra.lraId + "', " +
                            "'testcompleteurl', 'testcompensateurl', 'teststatusurl', '"+compensatorLink+"')");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    @POST
    @Path("/getAllLRAs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllLRAs() {
        return Response.ok().build();
    }

    /**
     * Purge LRA records that are marked for deletion
     * as well as any participant records/config if inactive for 24hr default //todo make this configurable
     * This is conducted whenever 1000 new records have been entered //todo make this configurable as well and potentially time based as well
     */
    void purge(){
        if (timeSinceLastPurge + (5 * 60 +1000) < System.currentTimeMillis() ) {
            // lraRecoveryRecordMap
            String sqlString = "delete lrarecords  where lraid ='" + "lraRecoveryRecordMap item" + "'";
            LOGGER.info("LRARecordPersistence.delete... " + sqlString);
            try {
                connection.createStatement().execute(sqlString);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }
}
