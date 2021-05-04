/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.connect;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class OciOpcService implements Service {
    static final String PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\n"
            + "MIIEpAIBAAKCAQEA2Oq92DVdRGDOEowitXIF/5oOivJOVbNq/K5Aams7yTY88HZr\n"
            + "02YIe51TapJ2HQ1C1uSObsvGtI91GaX1H24qkB7rn8yVJFfQC5A0AX9rC1n5qm1G\n"
            + "CMF4Y7CU0YdvMyQGGf8/odB3qmeTEyaO6d0LLtZebyY3IoefWZQFqtCF+urfKzMd\n"
            + "7PICmGZlMcbtsHR2pQFeL03pykh2+wnp8iFooVkb+66oeOBkKSpBJOiw37Oo7Jpg\n"
            + "xl6XdPA7seiESuQ4skgm+eVdQjpyf9o34CGl2zi7crs+XlJ3i9Ed/2E+EOlbMRcb\n"
            + "F+J3goTTe33aEg8Y2HxrfzHAfpNs1TU0Hp2c1QIDAQABAoIBAALKi0fUo/V+lpg+\n"
            + "TZU7hhOiz7+xwrIV0KSD7EtFdviUJjBkuoCqLIv4Qcigx6LnEHs2zkhjS3rEtjNV\n"
            + "MEEuLmw6IaEMRZNsPj6WPyN3y5C5U/ebJI51r6iX7v1+0/HHnsmt2aVDSyiAeny0\n"
            + "5c9HkcT4BP4PWVOsUa+zsmdfLu73TN0Eia9YHfTs2hCzJyydgO2IhSN4sPFRPKzW\n"
            + "yRQUr8Z8k+gVXQ8fdxgrbrKfSVnppX1fACbG8HfTJ8RTt1dgSe4+HLNIwYF34ZjS\n"
            + "P0ylnsT5V9JceiQhNSAgBSOJjOdVdjIyxdfLFVi5OSQU7KK1JrlHGvLFhvQ5Ah6M\n"
            + "OQ14068CgYEA9vTb3jllsbQy/2xpJFKAov/35dsCwuEZbJ7DFuNQpo61ApW18F/c\n"
            + "5o265qn43ETrmVG6s/8qA+zn5zgGVjj7MtMPblaPJX5Ny4Da76xy4m16Tg5z0Rvg\n"
            + "7LY9Xjcdw7mGCPmPGDcdIgxh0j9URUWVBrTZbih+xIiJ7nXzy5du1sMCgYEA4NxF\n"
            + "fT2FD3WbnaR0MqeLWv66J+qU6kFQ68xdzKHnyC7ECWl4wHMyW0NM+AvlXYuDPbY6\n"
            + "b279rWBCArrxjNi9L3tVk+B9Kzh3+Ii2kiczNKlr+qvr9eyj23uV2tAZVH21fQCA\n"
            + "7PCzQmUtJ6KRH5x8jPRctJSDaDqnu0gMzLUedIcCgYEAlua8SygredCwsN8fyDAZ\n"
            + "poBejDetkkNV88d3Uk8Igx4EgVXV3NHW+5JzOGt4Q7Bhfkgwm7g5hjiG5ASZ6qna\n"
            + "5Q2PCk8eHTz2cHmGTpnDgZR//Z5bKtWsNTiOezmWmHiO7IEB7TwQMzP5ui00YzfH\n"
            + "fleX3PYlsBX2op20oR0hf10CgYEApS1p5lwguIB+Nckuil9FISzpdpT9my8r0Gsp\n"
            + "pD5y0zx3SSqvRz/YB+5iRfwHGzZ2zAhm2KDBvHBvTS4iboJwRsbk0GIh0HQEvQ9A\n"
            + "fhBJry+dYGCWTursWzhnlnszgDtv7ElIa8VNCULlbq4eyQfc/nYq+4P1G8WqnQqK\n"
            + "TH3nx1UCgYAB2fTsUdbIrQKuLdOmjC9REeMJhyRHXQcoXASCHgzmFabEB+2gdYRm\n"
            + "dDSmA/rdCTYieHQlVIcxCX46yL+SwX9gbgrRKEzKuxuVdOiLdQZRM1UTiz7wkeOB\n"
            + "uBpQcbp8KskAq/wGkH7cnP+X1bTeh6fKTPz8/bN4SukPtipnW3jROw==\n"
            + "-----END RSA PRIVATE KEY-----";
    private static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
            + "MIIItjCCBp6gAwIBAgIRAIGp06bhtEMmM3CeKDT1aNswDQYJKoZIhvcNAQELBQAw\n"
            + "gasxczBxBgNVBAsTam9wYy1kZXZpY2U6MzI6MTY6OWU6ZTE6MDg6N2U6NTk6MDc6\n"
            + "Y2Y6Yjk6ZDc6NGQ6YTg6ZGE6MWM6ODU6NzM6Njg6YmE6Y2U6NWY6YjY6NzY6OGI6\n"
            + "ZjE6ZGI6MDg6MGQ6NTg6NGY6Nzg6NjQxNDAyBgNVBAMTK1BLSVNWQyBJZGVudGl0\n"
            + "eSBJbnRlcm1lZGlhdGUgZXUtZnJhbmtmdXJ0LTEwHhcNMjEwNTAzMTI1MDI2WhcN\n"
            + "MjEwNTAzMTQ1MTI2WjCCAc4xZzBlBgNVBAMTXm9jaWQxLmluc3RhbmNlLm9jMS5l\n"
            + "dS1mcmFua2Z1cnQtMS5hbnRoZWxqdGF1cXNxc2FjN2VudWw0bTI3dDc3b21xZW5m\n"
            + "Zm1kZTRnbHhjd21zNnRoYXN4Y3N0Z29ibHExHjAcBgNVBAsTFW9wYy1jZXJ0dHlw\n"
            + "ZTppbnN0YW5jZTFoMGYGA1UECxNfb3BjLWNvbXBhcnRtZW50Om9jaWQxLnRlbmFu\n"
            + "Y3kub2MxLi5hYWFhYWFhYWxkaTR1Nmw3a2tleTd1NXVoYnM0cWxkeDNzZm5waGw1\n"
            + "ZXpqeGJwYW94eGJjd3R1dmwyNWExdDByBgNVBAsTa29wYy1pbnN0YW5jZTpvY2lk\n"
            + "MS5pbnN0YW5jZS5vYzEuZXUtZnJhbmtmdXJ0LTEuYW50aGVsanRhdXFzcXNhYzdl\n"
            + "bnVsNG0yN3Q3N29tcWVuZmZtZGU0Z2x4Y3dtczZ0aGFzeGNzdGdvYmxxMWMwYQYD\n"
            + "VQQLE1pvcGMtdGVuYW50Om9jaWQxLnRlbmFuY3kub2MxLi5hYWFhYWFhYWxkaTR1\n"
            + "Nmw3a2tleTd1NXVoYnM0cWxkeDNzZm5waGw1ZXpqeGJwYW94eGJjd3R1dmwyNWEw\n"
            + "ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDY6r3YNV1EYM4SjCK1cgX/\n"
            + "mg6K8k5Vs2r8rkBqazvJNjzwdmvTZgh7nVNqknYdDULW5I5uy8a0j3UZpfUfbiqQ\n"
            + "HuufzJUkV9ALkDQBf2sLWfmqbUYIwXhjsJTRh28zJAYZ/z+h0HeqZ5MTJo7p3Qsu\n"
            + "1l5vJjcih59ZlAWq0IX66t8rMx3s8gKYZmUxxu2wdHalAV4vTenKSHb7CenyIWih\n"
            + "WRv7rqh44GQpKkEk6LDfs6jsmmDGXpd08Dux6IRK5DiySCb55V1COnJ/2jfgIaXb\n"
            + "OLtyuz5eUneL0R3/YT4Q6VsxFxsX4neChNN7fdoSDxjYfGt/McB+k2zVNTQenZzV\n"
            + "AgMBAAGjggKtMIICqTATBgNVHSUEDDAKBggrBgEFBQcDAjAfBgNVHSMEGDAWgBSK\n"
            + "h30gzLE7YlLSphK478fOBZyA0jCCAm8GCSsGAQQBb2IKAQSCAmAwggJcgQhpbnN0\n"
            + "YW5jZYJeb2NpZDEuaW5zdGFuY2Uub2MxLmV1LWZyYW5rZnVydC0xLmFudGhlbGp0\n"
            + "YXVxc3FzYWM3ZW51bDRtMjd0NzdvbXFlbmZmbWRlNGdseGN3bXM2dGhhc3hjc3Rn\n"
            + "b2JscYNPb2NpZDEudGVuYW5jeS5vYzEuLmFhYWFhYWFhbGRpNHU2bDdra2V5N3U1\n"
            + "dWhiczRxbGR4M3NmbnBobDVlemp4YnBhb3h4YmN3dHV2bDI1YYRPb2NpZDEudGVu\n"
            + "YW5jeS5vYzEuLmFhYWFhYWFhbGRpNHU2bDdra2V5N3U1dWhiczRxbGR4M3NmbnBo\n"
            + "bDVlemp4YnBhb3h4YmN3dHV2bDI1YYWCAUxBUUVDQVIrTENBQUFBQUFBQUFDTmtN\n"
            + "RnF3ekFNaG1HUEl2QXR5U3pYbzJsT295dUQ3ckFPVW5iWXpjUktNRGgyc1oxQlZ2\n"
            + "cnVTMUk2ZXR4SjhQMlNQcVNITTN4U2lNWTdxRENEZlh3ZnJIME5SSzBQL1ZGMUVh\n"
            + "b1VCcm9sTzJxTkkzME5XbVhqbE9oN2RnYkJFQm1XN0JCVVl5bWY4WW9oMnpETzhD\n"
            + "V1FTcVMzSTFRZ0p5aVozRXdGWjRWZitvMG1sMHdhRytzSEhTbDhtNFllays5VkxL\n"
            + "eHlIWVhucmxmR0ZvM3ZPV1Qva1IzY24wekltMHh3Z1RsZjVhSThvcXllMXBXUVJi\n"
            + "a1dYeHd1R2RSMjZENkNQMUZJaHBhYjVyMXYwYnZhL05EZWJjYzBZeEFaaW1YZytw\n"
            + "dDZqSW51Zm5iNUJSeG0zMWhjQVFBQTANBgkqhkiG9w0BAQsFAAOCAgEAerV4ZPmG\n"
            + "PfYgSCdytTAwmICMCUHw7+J0mEe4oUI6L8bGdXuH/cKvWYZLmwN9J47j8GnGfOo+\n"
            + "FULRD1OHB8JfkSxAT6U5WYaLgIeB5pWTy8E9cbFSlViiwbAD6IYoVKQ4ZmFTXlrT\n"
            + "EBFxvXcxwMF/59R+nsKwW9My5gqpYzAKHEEu68GDzgYlg0wm0ka10PQFZP+4wFG3\n"
            + "xy+BvLf6F06iJJQiJ7ZTBqtSMNmyL0jEi85YuAZkXMFfDmPGlq2JgF48F5rMVNcf\n"
            + "HhOWOvYbvWO88CKXmMllx0EkPL7C972bkH1DlbFaCBhuzCLDBh7TR29Y/KmgWwkl\n"
            + "/R0b561mYzJmYrD9pXNi/7mDkctTFjMfuoiLjlwUKs3+L3YNah/bNbTeZiMWlkDg\n"
            + "PRXR0GXZoMOBTbF0RaY0FuIK3GF2CvKwcHbTQfr0+ejCTQjbMXySFkRp/fW7QhMu\n"
            + "znbSNctpFfpNbxGJjgre62MhFqkoZA6VXq87aSJdtlf3y3k1HHywkZkScWJz4Tr+\n"
            + "DPAFlgUA8dZGP3PA+VFIg1AuG4S0ET8S8JNHfMfW0fMZV1zYmx/sohwll8VBtuii\n"
            + "JI1xKlRDscr9Hpn+hbLxEAwC/BhXS82zkmdOdo67TRjhQV8ua6j+DkgRn/679gLT\n"
            + "VVibNgn4fdRV2yK+5LVnMcTAjcheVSxQQPE=\n"
            + "-----END CERTIFICATE-----";

    private static final String INTERMEDIATE = "-----BEGIN CERTIFICATE-----\n"
            + "MIIF3zCCA8egAwIBAgIQWnCdgMJWq9JemFsT3doTbzANBgkqhkiG9w0BAQsFADBD\n"
            + "MUEwPwYDVQQDEzhPQ0kgSW5zdGFuY2UgSWRlbnRpdHkgUm9vdCAtIGV1LWZyYW5r\n"
            + "ZnVydC0xIC0gMTUzMzI0MTA5MTAeFw0yMTA1MDIyMTEwMTZaFw0yMjA1MDIyMTEx\n"
            + "MTZaMIGrMXMwcQYDVQQLE2pvcGMtZGV2aWNlOjk3OjMzOjg3OjcxOmQwOmFmOjc0\n"
            + "OjBhOmY3OmE0OmQ4OmNmOjk4OmYzOjJkOmIzOjk1OjBjOjMwOmM5OmUwOjQ0OmMz\n"
            + "OmFhOmRhOmFiOmU3OmQ0Ojc1OmQ4OjBiOjMzMTQwMgYDVQQDEytQS0lTVkMgSWRl\n"
            + "bnRpdHkgSW50ZXJtZWRpYXRlIGV1LWZyYW5rZnVydC0xMIICIjANBgkqhkiG9w0B\n"
            + "AQEFAAOCAg8AMIICCgKCAgEAxfdzFByQqw3a7RPYk5HGFPWNPxWhVmMAj/immRtQ\n"
            + "4hb+HKAVfVniahHodoKC6uQEmCm2vaDAGIn1OJ+gliFKFWCQKSytEXIOGaTBtbnJ\n"
            + "5Dypw0++AlUKQHokPqljwuovaGKFiVrzlm53VN+YHTcFxEo7NSjiK8hkkNqw/Vgm\n"
            + "NMmLtQEYfsyjMZXxLFJuh9eVzDz5MRhp24yEpauWe15D7tZFmPjzPBxesnkTE1N/\n"
            + "j6Fs8UaQRnQKG+fae0B2/BlH/Ho40SLtVcWbNpdAiyjA63T2MXC9zem7X/M0DTGp\n"
            + "2z8uDwKAC/dTLoGX0vVZZJQ1sSUrp1VYgBNevjiDE8fky4n5h9x/C8YtayQl8UZm\n"
            + "snJOR3ogagG+Rk2763sN/7xpjH2FwArLOLw/Mxt+0RWKPIwssYAv0+mKLBb6mZZN\n"
            + "0TkRdTr5bLKE6lceKgnpLhhNAA+n+TaG95d8Ak68O5Omu40zUl7gjZ7Zp/z1Xfgs\n"
            + "bda9m7oCUdsgKBpHYesjxCCAs+KUBQOzTCKGzRqppqL/MDypMt9M6IAqoa9ZA40D\n"
            + "mfMsCkH6Q8wIqZ6O73ps57gDhOrphYHxoim05A5UAaU/a5rz0cCcyjs0hZwHq4iW\n"
            + "pYy/fbxLVC9C7mlaBwVPCTPoMSQwjnkc7NPdRr8/v1PlbSV+s5QndKmm4GwLM4KL\n"
            + "TtkCAwEAAaNmMGQwDgYDVR0PAQH/BAQDAgK8MBIGA1UdEwEB/wQIMAYBAf8CAQAw\n"
            + "HQYDVR0OBBYEFFgTh9yRVAGJRNBLiEUq7i9/11LUMB8GA1UdIwQYMBaAFBYuM60t\n"
            + "hL81HbMhrLf6L+7QdpyqMA0GCSqGSIb3DQEBCwUAA4ICAQCRwzpfaoRJcGxyppOF\n"
            + "JuwsoOmXl8JnIN8O79LEdFVg/UIYq8jQ8cinTBT2ySAgCIZi4T62PZgsPLqwrobh\n"
            + "EfW+7JQSPkT/2OrWXQq2/5iQ7axnDEKwzFIzFdY/KJ83ZcgDTWLam6dnKlzRg91o\n"
            + "EPwsiM/NQJBIvURdppj/aslHyyoIoD50gaz0xSGRZjKXnsJZ3nOzlN1vZuCUaLVz\n"
            + "baVS52cnx6/bQeOzHzcd+8Pt6t2GqwUOuVhF5D4FNzb+3AfYt+ROS76TMFDJVfsY\n"
            + "usSvLk8A6u08ODh8vX0m98j12OCJ4tIHNKzeEhHQ3wRoPrqQjPa7iXYu6ipTHztv\n"
            + "Bp/4isawsq7MIAipqljTSIkZr3R1qftEogbiSx9oONRuhbvzs8TyDQuV8oUfQBqe\n"
            + "dJ7HaXKA7rBpaxHVchdT66pv5xZC/e0+yMfFV+Kt1Mk78dw/TyFXCZgVUw5sNOWg\n"
            + "VQmE5JSh0JULDzCh3781UDAQ4VrMv9Sa+Cg3zAFU/dAM9F+6Lsyan8DOjyNpMSEl\n"
            + "GDAiOSvUs2xxqgHdtY6qA+1aLQyLqUhdkCxIWT9G3uxUwrex48FycC71oejNJQ6e\n"
            + "TT3I8SIs3IuCOpIAwma9osrVQ3hqfKuyHYLq4VUjo5kbk7nC85/H3MxqBb6xNYXI\n"
            + "mbj9Tcil0DWaQAc9GexOffTROQ==\n"
            + "-----END CERTIFICATE-----";

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/identity/key.pem", this::privateKey)
             .get("/identity/cert.pem", this::certificate)
             .get("/idientity/intermediate.pem", this::intermediate)
             .get("/instance/region", this::region);
    }

    private void privateKey(ServerRequest req, ServerResponse res) {
        res.headers().contentType(MediaType.create("application", "x-x509-ca-cert"));
        res.send(PRIVATE_KEY);
    }

    private void intermediate(ServerRequest req, ServerResponse res) {
        res.headers().contentType(MediaType.create("application", "x-x509-ca-cert"));
        res.send(INTERMEDIATE);
    }

    private void certificate(ServerRequest req, ServerResponse res) {
        res.headers().contentType(MediaType.create("application", "x-x509-ca-cert"));
        res.send(CERTIFICATE);
    }

    private void region(ServerRequest req, ServerResponse res) {
        res.headers().contentType(MediaType.TEXT_PLAIN);
        res.send("eu-frankfurt-1");
    }
}
