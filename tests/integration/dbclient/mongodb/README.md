# DbClient Integration Test MongoDB

To run this test:
```shell
mvn clean verify
```

Build the Docker image:
```shell
docker build etc/docker -t mongodb
```

Start the database:
```shell
docker run -d \
    --name mongodb \
    -e MONGO_DB=pokemon \
    -e MONGO_USER=test \
    -e MONGO_PASSWORD=mongo123 \
    -p 27017:27017 \
    mongodb
```
