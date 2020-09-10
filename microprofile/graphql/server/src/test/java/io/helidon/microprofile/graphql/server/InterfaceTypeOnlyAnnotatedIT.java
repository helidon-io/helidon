package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.IOException;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class InterfaceTypeOnlyAnnotatedIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(Vehicle.class)
                                                                .addBeanClass(Car.class)
                                                                .addBeanClass(Motorbike.class)
                                                                .addBeanClass(Motorbike.class)
                                                                .addBeanClass(VehicleIncident.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    /**
     * Test discovery of interfaces and subsequent unresolved type which has a Name annotation .
     */
    @Test
    public void testInterfaceDiscoveryWithUnresolvedType() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Vehicle.class, Car.class, Motorbike.class, VehicleIncident.class, AbstractVehicle.class);
        assertInterfaceResults();
    }

}
