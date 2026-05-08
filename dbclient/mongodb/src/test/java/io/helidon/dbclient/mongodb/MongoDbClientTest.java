/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.spi.DbMapperProvider;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("resource")
class MongoDbClientTest {

    private static final System.Logger LOGGER = System.getLogger(MongoDbClientTest.class.getName());
    private static MongoDbClient dbClient;

    @BeforeAll
    static void setup() {
        dbClient = createClient(null);
    }

    @Test
    void testUnwrapClientClass() {
        MongoClient connection = dbClient.unwrap(MongoClient.class);
        assertThat(connection, notNullValue());
        MongoDatabase db = dbClient.unwrap(MongoDatabase.class);
        assertThat(db, notNullValue());
    }

    @Test
    void testUnsupportedUnwrapClientClass() {
        try {
            dbClient.unwrap(MongoCollection.class);
            fail("Unsupported unwrap call must throw UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Caught expected UnsupportedOperationException: %s", ex.getMessage()));
        }
    }

    @Test
    void testUnwrapExecutorClass() {
        DbExecute exec = dbClient.execute();
        MongoDatabase connection = exec.unwrap(MongoDatabase.class);
        assertThat(connection, notNullValue());
        exec.query("{\"operation\": \"command\", \"query\": { ping: 1 }}");
    }

    @Test
    void testUnsupportedUnwrapExecutorClass() {
        DbExecute exec = dbClient.execute();
        try {
            exec.unwrap(MongoClient.class);
            fail("Unsupported unwrap call must throw UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Caught expected UnsupportedOperationException: %s"
                    , ex.getMessage()));
        }
        exec.query("{\"operation\": \"command\", \"query\": { ping: 1 }}");
    }

    @Test
    void testDbClientServiceQuery() {
        TestDbClientService service = new TestDbClientService();
        MongoDbClient dbClient = createClient(builder -> builder.addService(service));
        DbExecute exec = dbClient.execute();
        long _ = exec.query("{\"operation\": \"command\", \"query\": { ping: 1 }}").count();
        assertThat(service.resultFuture.isDone(), is(true));
        assertThat(service.resultFuture.isCompletedExceptionally(), is(false));
        assertThat(service.statementFuture.isDone(), is(true));
        assertThat(service.statementFuture.isCompletedExceptionally(), is(false));
    }

    @Test
    void testDbClientServiceDml() {
        TestDbClientService service = new TestDbClientService();
        MongoDbClient dbClient = createClient(builder -> builder.addService(service));
        DbExecute exec = dbClient.execute();
        long _ = exec.insert("""
                                        {
                                          "collection": "foo",
                                          "operation": "insert",
                                          "value": { "name": "bar" }
                                        }
                                        """);
        assertThat(service.resultFuture.isDone(), is(true));
        assertThat(service.resultFuture.isCompletedExceptionally(), is(false));
        assertThat(service.statementFuture.isDone(), is(true));
        assertThat(service.statementFuture.isCompletedExceptionally(), is(false));
    }

    @Test
    void testMappedNamedAddParamDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection, builder -> builder.dbMapperManager(dbMapperManager()));
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "foo",
                                        "operation": "insert",
                                        "value": { "pokemon": $pokemon }
                                      }
                                      """)
                .addParam("pokemon", new MappedPokemon(25, "Pikachu"))
                .execute();

        assertThat(result, is(1L));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(collection).insertOne(captor.capture());
        Document pokemon = captor.getValue().get("pokemon", Document.class);
        assertThat(pokemon.getInteger("id"), is(25));
        assertThat(pokemon.getString("name"), is("Pikachu"));
    }

    @Test
    void testMappedNamedParamDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection, builder -> builder.dbMapperManager(dbMapperManager()));
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "foo",
                                        "operation": "insert",
                                        "value": {
                                          "id": $id,
                                          "name": $name
                                        }
                                      }
                                      """)
                .namedParam(new MappedPokemon(7, "Squirtle"))
                .execute();

        assertThat(result, is(1L));
        assertInsertedPokemon(collection, 7, "Squirtle");
    }

    @Test
    void testMappedIndexedParamDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection, builder -> builder.dbMapperManager(dbMapperManager()));
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "foo",
                                        "operation": "insert",
                                        "value": {
                                          "id": ?,
                                          "name": ?
                                        }
                                      }
                                      """)
                .indexedParam(new MappedPokemon(133, "Eevee"))
                .execute();

        assertThat(result, is(1L));
        assertInsertedPokemon(collection, 133, "Eevee");
    }

    @Test
    void testMappedIndexedAddParamDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection, builder -> builder.dbMapperManager(dbMapperManager()));
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "foo",
                                        "operation": "insert",
                                        "value": ?
                                      }
                                      """)
                .addParam(new MappedPokemon(26, "Raichu"))
                .execute();

        assertThat(result, is(1L));

        assertInsertedPokemon(collection, 26, "Raichu");
    }

    @Test
    void testMappedIndexedParamsListDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection, builder -> builder.dbMapperManager(dbMapperManager()));
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "foo",
                                        "operation": "insert",
                                        "value": ?
                                      }
                                      """)
                .params(List.of(new MappedPokemon(39, "Jigglypuff")))
                .execute();

        assertThat(result, is(1L));
        assertInsertedPokemon(collection, 39, "Jigglypuff");
    }

    @Test
    void testMappedIndexedParamsVarargsDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection, builder -> builder.dbMapperManager(dbMapperManager()));
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "foo",
                                        "operation": "insert",
                                        "value": ?
                                      }
                                      """)
                .params(new MappedPokemon(52, "Meowth"))
                .execute();

        assertThat(result, is(1L));
        assertInsertedPokemon(collection, 52, "Meowth");
    }

    @Test
    void testMappedCollectionParamsDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection, builder -> builder.dbMapperManager(dbMapperManager()));
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "foo",
                                        "operation": "insert",
                                        "value": { "team": $team }
                                      }
                                      """)
                .params(Map.of("team", List.of(
                        new MappedPokemon(1, "Bulbasaur"),
                        new MappedPokemon(4, "Charmander"))))
                .execute();

        assertThat(result, is(1L));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(collection).insertOne(captor.capture());
        List<Document> team = captor.getValue().getList("team", Document.class);
        assertThat(team, notNullValue());
        assertThat(team.size(), is(2));
        assertThat(team.get(0).getInteger("id"), is(1));
        assertThat(team.get(0).getString("name"), is("Bulbasaur"));
        assertThat(team.get(1).getInteger("id"), is(4));
        assertThat(team.get(1).getString("name"), is("Charmander"));
    }

    @Test
    void testIssue10819NamedParamDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection,
                                              builder -> builder.dbMapperManager(issue10819DbMapperManager()));
        Issue10819Product product = issue10819Product();
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "products",
                                        "operation": "insert",
                                        "value": {
                                          "id": $id,
                                          "name": $name,
                                          "price": $price,
                                          "bestBefore": $bestBefore,
                                          "category": $category,
                                          "version": $version,
                                          "reviews": $reviews
                                        }
                                      }
                                      """)
                .namedParam(product)
                .execute();

        assertThat(result, is(1L));
        assertInsertedIssue10819Product(collection, product);
    }

    @Test
    void testIssue10819NamedParamsMapDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection,
                                              builder -> builder.dbMapperManager(issue10819DbMapperManager()));
        Issue10819Product product = issue10819Product();
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "products",
                                        "operation": "insert",
                                        "value": {
                                          "id": $id,
                                          "name": $name,
                                          "price": $price,
                                          "bestBefore": $bestBefore,
                                          "category": $category,
                                          "version": $version,
                                          "reviews": $reviews
                                        }
                                      }
                                      """)
                .params(Map.of("product", product))
                .execute();

        assertThat(result, is(1L));
        assertInsertedIssue10819Product(collection, product);
    }

    @Test
    void testIssue10819IndexedParamDml() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection,
                                              builder -> builder.dbMapperManager(issue10819DbMapperManager()));
        Issue10819Product product = issue10819Product();
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "products",
                                        "operation": "insert",
                                        "value": {
                                          "id": ?,
                                          "name": ?,
                                          "price": ?,
                                          "bestBefore": ?,
                                          "category": ?,
                                          "version": ?,
                                          "reviews": ?
                                        }
                                      }
                                      """)
                .indexedParam(product)
                .execute();

        assertThat(result, is(1L));
        assertInsertedIssue10819Product(collection, product);
    }

    @Test
    void testIssue10819AttachedReproducer() {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        MongoDbClient dbClient = createClient(collection,
                                              builder -> builder.dbMapperManager(issue10819DbMapperManager()));
        Issue10819Product product = issue10819Product();
        long result = dbClient.execute()
                .createInsert("""
                                      {
                                        "collection": "products",
                                        "operation": "insert",
                                        "value": {
                                          "id": $id,
                                          "name": $name,
                                          "price": $price,
                                          "bestBefore": $bestBefore,
                                          "category": $category,
                                          "version": $version,
                                          "reviews": $reviews
                                        }
                                      }
                                      """)
                .addParam("product", product)
                .execute();

        assertThat(result, is(1L));

        assertInsertedIssue10819Product(collection, product);
    }

    private static void assertInsertedPokemon(MongoCollection<Document> collection,
                                              int expectedId,
                                              String expectedName) {
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(collection).insertOne(captor.capture());
        assertThat(captor.getValue().getInteger("id"), is(expectedId));
        assertThat(captor.getValue().getString("name"), is(expectedName));
    }

    private static void assertInsertedIssue10819Product(MongoCollection<Document> collection,
                                                        Issue10819Product product) {
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(collection).insertOne(captor.capture());
        Document inserted = captor.getValue();
        assertThat(inserted.getInteger("id"), is(product.getId()));
        assertThat(inserted.getString("name"), is(product.getName()));
        assertThat(inserted.getDouble("price"), is(product.getPrice().doubleValue()));
        assertThat(inserted.getString("bestBefore"), is(product.getBestBefore().toString()));
        assertThat(inserted.getString("category"), is(product.getCategory()));
        assertThat(inserted.getInteger("version"), is(product.getVersion()));
        assertThat(inserted.getList("reviews", Object.class), is(product.getReviews()));
    }

    private static Issue10819Product issue10819Product() {
        return new Issue10819Product(110,
                                     "Cake",
                                     BigDecimal.valueOf(2.99),
                                     LocalDate.parse("2025-11-05"),
                                     "Food",
                                     1);
    }

    @SuppressWarnings("unchecked")
    static MongoDbClient createClient(Consumer<MongoDbClientBuilder> consumer) {
        return createClient(null, consumer);
    }

    @SuppressWarnings("unchecked")
    static MongoDbClient createClient(MongoCollection<Document> collection, Consumer<MongoDbClientBuilder> consumer) {
        MongoClient client = Mockito.mock(MongoClient.class);
        MongoDatabase db = Mockito.mock(MongoDatabase.class);
        MongoCollection<Document> mongoCollection = collection == null ? Mockito.mock(MongoCollection.class) : collection;
        when(db.getCollection(any())).thenReturn(mongoCollection);
        when(db.runCommand(any())).thenReturn(MongoDbStatement.EMPTY);
        MongoDbClientBuilder builder = new MongoDbClientBuilder();
        if (consumer != null) {
            consumer.accept(builder);
        }
        if (builder.dbMapperManager() == null) {
            builder.dbMapperManager(DbMapperManager.builder().build());
        }
        return new MongoDbClient(builder, client, db);
    }

    record TestDbClientService(CompletableFuture<Long> resultFuture,
                               CompletableFuture<Void> statementFuture) implements DbClientService {

        TestDbClientService() {
            this(new CompletableFuture<>(), new CompletableFuture<>());
        }

        @Override
        public DbClientServiceContext statement(DbClientServiceContext context) {
            setup(context.resultFuture(), resultFuture);
            setup(context.statementFuture(), statementFuture);
            return context;
        }

        static <T> void setup(CompletionStage<T> stage, CompletableFuture<T> future) {
            stage.whenComplete((v, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                } else {
                    future.complete(v);
                }
            });
        }
    }

    record MappedPokemon(int id, String name) {
    }

    private static final class Issue10819Product {
        private final int id;
        private final String name;
        private final BigDecimal price;
        private final LocalDate bestBefore;
        private final String category;
        private final int version;
        private final List<Issue10819Review> reviews = new ArrayList<>();

        private Issue10819Product(int id, String name, BigDecimal price, LocalDate bestBefore, String category, int version) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.bestBefore = bestBefore;
            this.category = category;
            this.version = version;
        }

        int getId() {
            return id;
        }

        String getName() {
            return name;
        }

        BigDecimal getPrice() {
            return price;
        }

        LocalDate getBestBefore() {
            return bestBefore;
        }

        String getCategory() {
            return category;
        }

        int getVersion() {
            return version;
        }

        List<Issue10819Review> getReviews() {
            return reviews;
        }
    }

    private record Issue10819Review(String review, int rating) {
    }

    private static final class PokemonMapperProvider implements DbMapperProvider {
        private static final DbMapper<MappedPokemon> POKEMON_MAPPER = new DbMapper<>() {
            @Override
            public MappedPokemon read(DbRow row) {
                throw new UnsupportedOperationException("Read operation is not implemented.");
            }

            @Override
            public Map<String, ?> toNamedParameters(MappedPokemon value) {
                return Map.of(
                        "id", value.id(),
                        "name", value.name());
            }

            @Override
            public List<?> toIndexedParameters(MappedPokemon value) {
                return List.of(value.id(), value.name());
            }
        };

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
            if (type.equals(MappedPokemon.class)) {
                return Optional.of((DbMapper<T>) POKEMON_MAPPER);
            }
            return Optional.empty();
        }
    }

    private static final class Issue10819ProductMapper implements DbMapper<Issue10819Product> {
        @Override
        public Issue10819Product read(DbRow row) {
            throw new UnsupportedOperationException("Read operation is not implemented.");
        }

        @Override
        public Map<String, ?> toNamedParameters(Issue10819Product product) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", product.getId());
            map.put("name", product.getName());
            map.put("price", product.getPrice().doubleValue());
            if (product.getBestBefore() != null) {
                map.put("bestBefore", product.getBestBefore().toString());
            }
            map.put("category", product.getCategory());
            map.put("version", product.getVersion());
            if (product.getReviews() != null) {
                map.put("reviews", product.getReviews());
            }
            return map;
        }

        @Override
        public List<?> toIndexedParameters(Issue10819Product product) {
            return List.copyOf(toNamedParameters(product).values());
        }
    }

    private static final class Issue10819ProductMapperProvider implements DbMapperProvider {
        private static final DbMapper<Issue10819Product> PRODUCT_MAPPER = new Issue10819ProductMapper();

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
            if (type.equals(Issue10819Product.class)) {
                return Optional.of((DbMapper<T>) PRODUCT_MAPPER);
            }
            return Optional.empty();
        }
    }

    private static DbMapperManager dbMapperManager() {
        return DbMapperManager.builder()
                .addMapperProvider(new PokemonMapperProvider())
                .build();
    }

    private static DbMapperManager issue10819DbMapperManager() {
        return DbMapperManager.builder()
                .addMapperProvider(new Issue10819ProductMapperProvider())
                .build();
    }
}
