Cassandra Docker Image
========================================

```bash
mvn generate-resources docker:build

docker run -d --name helidon-todos-cassandra -p 9042:9042 helidon.demos/io/helidon/demo/helidon-todos-cassandra:latest
```

