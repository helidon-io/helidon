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

- Update ./pom.xml to replace dependency on micronaut-jdbc-hikari with following
```
        <dependency>
            <groupId>io.micronaut.sql</groupId>
            <artifactId>micronaut-jdbc-ucp</artifactId>
            <scope>runtime</scope>
        </dependency>
```

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
  
- Update ./src/main/resources/META-INF/microprofile-config.properties to comment out h2 related datasource.* properties and uncomment+update following ones related to XE.
```
#datasources.default.url=jdbc:oracle:thin:@localhost:<port for db>/<sid of db>
#datasources.default.driverClassName=oracle.jdbc.OracleDriver
#datasources.default.username=system
#datasources.default.password=<your system user password>
#datasources.default.schema-generate=CREATE_DROP
#datasources.default.dialect=oracle
```

# To use Oracle ATP cloud service instead of H2

- Update ./pom.xml to replace dependency on micronaut-jdbc-hikari with following
```
        <dependency>
            <groupId>io.micronaut.sql</groupId>
            <artifactId>micronaut-jdbc-ucp</artifactId>
            <scope>runtime</scope>
        </dependency>
```

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

- Create Schema used by test
```
CREATE TABLE "PET" ("ID" VARCHAR(36),"OWNER_ID" NUMBER(19) NOT NULL,"NAME" VARCHAR(255) NOT NULL,"TYPE" VARCHAR(255) NOT NULL);
CREATE SEQUENCE "OWNER_SEQ" MINVALUE 1 START WITH 1 NOCACHE NOCYCLE;
CREATE TABLE "OWNER" ("ID" NUMBER(19) PRIMARY KEY NOT NULL,"AGE" NUMBER(10) NOT NULL,"NAME" VARCHAR(255) NOT NULL);
```

- Update ./src/main/resources/META-INF/microprofile-config.properties to comment out h2 related datasource.* properties and add uncomment+update following ones related to ATP.
```
#datasources.default.url=jdbc:oracle:thin:@<your atp instance>?TNS_ADMIN=<path to your wallet file>
#datasources.default.driverClassName=oracle.jdbc.OracleDriver
#datasources.default.username=<your atp user>
#datasources.default.password=<your atp password>
#datasources.default.schema-generate=NONE
#datasources.default.dialect=oracle
```