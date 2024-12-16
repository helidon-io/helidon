# DbClient Integration Test PostgreSQL

To run this test:
```shell
mvn clean verify
```

Build the Docker image:
```shell
docker build etc/docker -t pgsql
```

Start the database:
```shell
docker run -d \
    --name pgsql \
    -e POSTGRES_USER=test \
    -e POSTGRES_PASSWORD=pgsql123 \
    -e POSTGRES_DB=test \
    -p 5432:5432 \
    pgsql
```
