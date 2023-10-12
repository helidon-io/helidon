# TODO Demo Application

This application implements todomvc[http://todomvc.com] with two microservices
implemented with Helidon MP and Helidon SE.

## Build

```bash
mvn clean package
docker build -t helidon-examples-todo-cassandra cassandra
```

## Run

```bash
docker run -d -p 9042:9042 --name helidon-examples-todo-cassandra helidon-examples-todo-cassandra
docker run --name zipkin -d -p 9411:9411 openzipkin/zipkin
java -jar backend/target/helidon-examples-todo-backend.jar &
java -jar frontend/target/helidon-examples-todo-frontend.jar &
```

- Open http://localhost:8080 in your browser
- Login with a Google account
- Add some TODO entries
- Check-out the traces at http://localhost:9411

### HTTP proxy

If you want to run behind an HTTP proxy:

```bash
export security_providers_0_google_dash_login_proxy_dash_host=proxy.acme.com
export security_providers_0_google_dash_login_proxy_dash_port=80
```

## Stop

```bash
kill %1 %2
docker rm -f zipkin helidon-examples-todo-cassandra
```
