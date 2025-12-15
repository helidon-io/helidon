### Configure the application

Make sure, your application has access to your OCI setup. One way, you can do so, if your kubernetes cluster is running locally, is by volume.

Create a volume pointing to your OCI configuration file
```yaml
      volumes:
        - name: oci-config
          hostPath:
            # directory location on host
            path: <Directory with oci config file>
```

Mount this volume as part of your application containers specification
```yaml
        volumeMounts:
        - mountPath: /root/.oci
          name: oci-config
```
