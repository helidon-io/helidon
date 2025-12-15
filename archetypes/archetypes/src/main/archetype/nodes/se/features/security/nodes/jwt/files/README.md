## Try JWT

```bash
curl -u "jack:changeit" http://localhost:8080/propagate
curl -u "jack:changeit" http://localhost:8080/override
curl -u "jill:changeit" http://localhost:8080/propagate
curl -u "jill:changeit" http://localhost:8080/override
```
