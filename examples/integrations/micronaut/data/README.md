# Helidon Micronaut Data Example

This example shows integration with Micronaut Data into Helidon MP.

## Sources

This example combines Micronaut Data and CDI.

### CDI classes

The following classes are CDI and JAX-RS classes that use injected Micronaut beans:

- `PetResource` - JAX-RS resource exposing Pet REST API
- `OwnerResource` - JAX-RS resource exposing Owner REST API
- `BeanValidationExceptionMapper` - JAX-RS exception mapper to return correct status code
        in case of validation failure

### Micronaut classes

The following classes are pure Micronaut beans (and cannot have CDI injected into them)

- `DbPetRepository` - Micronaut Data repository extending an abstract class
- `DbOwnerRepository` - Micronaut Data repository implementing an interface
- `DbPopulateData` - Micronaut startup event listener to initialize the database
- package `model` - data model of the database

## Build and run

Start the application:

```bash
mvn package
java -jar target/helidon-examples-integrations-micronaut-data.jar
```

Access endpoints

```bash
# Get all pets
curl -i http://localhost:8080/pets
# Get all owners
curl -i http://localhost:8080/owners
# Get a single pet
curl -i http://localhost:8080/pets/Dino
# Get a single owner
curl -i http://localhost:8080/owners/Barney
# To fail input validation
curl -i http://localhost:8080/pets/s
```

# To use Oracle XE instead of H2

- Update ./pom.xml to replace dependency on com.h2database with following
```
        <dependency>
            <groupId>com.oracle.database.jdbc</groupId>
            <artifactId>ojdbc8-production</artifactId>
            <type>pom</type>
            <scope>runtime</scope>
        </dependency>
```

- Update ./src/main/java/io/helidon/examples/integrations/micronaut/data/DbOwnerRepository.java and 
  ./src/main/java/io/helidon/examples/integrations/micronaut/data/DbOwnerRepository.java to change from 
  Dialect.H2 to Dialect.ORACLE
  
- Update ./src/main/java/io/helidon/examples/integrations/micronaut/data/DbPopulateData.java to change Typehint to
  @TypeHint(typeNames = {"oracle.jdbc.OracleDriver"})

- Install Oracle XE
  Instructions for running XE in a docker container can be found here: https://github.com/oracle/docker-images/tree/master/OracleDatabase/SingleInstance
  
- Update ./src/main/resources/META-INF/microprofile-config.properties to comment out h2 related datasource.* properties and add following ones related to oracle.
```
datasources.default.url=jdbc:oracle:thin:@localhost:1521/XE
datasources.default.driverClassName=oracle.jdbc.OracleDriver
datasources.default.username=system
datasources.default.password=<password for system user>
datasources.default.schema-generate=CREATE_DROP
datasources.default.dialect=oracle
```

# To use Oracle ATP cloud service instead of H2

- Update ./pom.xml to replace dependency on com.h2database with following
```
        <dependency>
            <groupId>com.oracle.database.jdbc</groupId>
            <artifactId>ojdbc8-production</artifactId>
            <type>pom</type>
            <scope>runtime</scope>
        </dependency>
```

- Update ./src/main/java/io/helidon/examples/integrations/micronaut/data/DbOwnerRepository.java and
  ./src/main/java/io/helidon/examples/integrations/micronaut/data/DbOwnerRepository.java to change from
  Dialect.H2 to Dialect.ORACLE

- Update ./src/main/java/io/helidon/examples/integrations/micronaut/data/DbPopulateData.java to change Typehint to
  @TypeHint(typeNames = {"oracle.jdbc.OracleDriver"})
  
- Setup ATP 
  Instructions for ATP setup can be found here: https://blogs.oracle.com/developers/the-complete-guide-to-getting-up-and-running-with-autonomous-database-in-the-cloud

- Update ./src/main/resources/META-INF/microprofile-config.properties to comment out h2 related datasource.* properties and add following ones related to oracle.
```
datasources.default.url=jdbc:oracle:thin:@<your_atp>>?TNS_ADMIN=<Path to expanded wallet directory>
datasources.default.driverClassName=oracle.jdbc.OracleDriver
datasources.default.username=admin
datasources.default.password=<password for admin user>
datasources.default.schema-generate=NONE
datasources.default.dialect=oracle
```