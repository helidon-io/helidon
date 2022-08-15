h2load benchmark
---

Date: 19th December 2021

Using the nghttp `h2load` tool with the following configurations:

```shell
h2load -n 10000000 -t 5 -c 5 -m 100 http://127.0.0.1:8080/plaintext
h2load -n 10000000 -t 5 -c 5 -m 100 https://127.0.0.1:8081/plaintext
```

## Results

Plaintext on MacOS (Intel i9, 8cores):

```
finished in 8.36s, 1195571.89 req/s, 69.55MB/s
requests: 10000000 total, 10000000 started, 10000000 done, 10000000 succeeded, 0 failed, 0 errored, 0 timeout
status codes: 10000000 2xx, 0 3xx, 0 4xx, 0 5xx
traffic: 581.74MB (610000260) total, 286.10MB (300000140) headers (space savings 72.22%), 123.98MB (130000000) data
                     min         max         mean         sd        +/- sd
time for request:       68us     44.54ms       339us       443us    95.76%
time for connect:      232us       345us       297us        45us    60.00%
time to 1st byte:     1.71ms      1.87ms      1.82ms        65us    80.00%
req/s           :  239120.33   243400.54   241617.01     1597.51    60.00%
```

TLS on MacOS (Intel i9, 8cores):

```
TLS Protocol: TLSv1.3
Cipher: TLS_AES_128_GCM_SHA256
Server Temp Key: X25519 253 bits
Application protocol: h2

...

finished in 11.50s, 869508.43 req/s, 50.58MB/s
requests: 10000000 total, 10000000 started, 10000000 done, 10000000 succeeded, 0 failed, 0 errored, 0 timeout
status codes: 10000000 2xx, 0 3xx, 0 4xx, 0 5xx
traffic: 581.74MB (610000260) total, 286.10MB (300000140) headers (space savings 72.22%), 123.98MB (130000000) data
                     min         max         mean         sd        +/- sd
time for request:       89us    116.91ms       395us       290us    94.48%
time for connect:     7.91ms     42.73ms     25.29ms     13.65ms    60.00%
time to 1st byte:     9.18ms     44.28ms     26.59ms     13.82ms    60.00%
req/s           :  173905.00   178084.78   175338.75     1779.28    80.00%
```
