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

> void updateGreeting(message)

Set the greeting to use in future messages.

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **message** | [**Message**](Message.md)| Message for the user | |

### Return type

[**void**](Void.md)

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

