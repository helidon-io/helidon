MySQL:
```
docker run --rm --name mysql -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD=root \
    -e MYSQL_DATABASE=pokemon \
    -e MYSQL_USER=user \
    -e MYSQL_PASSWORD=changeit \
    container-registry.oracle.com/mysql/community-server:9.7.3
```
