# MessageApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getDefaultMessage**](MessageApi.md#getDefaultMessage) | **GET** /greet | Return a worldly greeting message. |
| [**getMessage**](MessageApi.md#getMessage) | **GET** /greet/{name} | Return a greeting message using the name that was provided. |
| [**updateGreeting**](MessageApi.md#updateGreeting) | **PUT** /greet/greeting | Set the greeting to use in future messages. |



## getDefaultMessage

> Message getDefaultMessage()

Return a worldly greeting message.

### Example

```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.MessageApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("http://localhost:8080");

        MessageApi apiInstance = new MessageApi(defaultClient);
        try {
            Message result = apiInstance.getDefaultMessage();
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling MessageApi#getDefaultMessage");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**Message**](Message.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | successful operation |  -  |


## getMessage

> Message getMessage(name)

Return a greeting message using the name that was provided.

### Example

```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.MessageApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("http://localhost:8080");

        MessageApi apiInstance = new MessageApi(defaultClient);
        String name = "name_example"; // String | the name to greet
        try {
            Message result = apiInstance.getMessage(name);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling MessageApi#getMessage");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **name** | **String**| the name to greet | |

### Return type

[**Message**](Message.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | successful operation |  -  |


## updateGreeting

> updateGreeting(message)

Set the greeting to use in future messages.

### Example

```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.MessageApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("http://localhost:8080");

        MessageApi apiInstance = new MessageApi(defaultClient);
        Message message = new Message(); // Message | Message for the user
        try {
            apiInstance.updateGreeting(message);
        } catch (ApiException e) {
            System.err.println("Exception when calling MessageApi#updateGreeting");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **message** | [**Message**](Message.md)| Message for the user | |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | successful operation |  -  |
| **400** | No greeting provided |  -  |

