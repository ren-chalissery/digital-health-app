# ReflectionsApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**deleteReflection**](ReflectionsApi.md#deleteReflection) | **DELETE** api/v1/me/reflections/{reflectionId} | Delete one |
| [**editReflection**](ReflectionsApi.md#editReflection) | **PUT** api/v1/me/reflections/{reflectionId} | Edit one |
| [**getReflection**](ReflectionsApi.md#getReflection) | **GET** api/v1/me/reflections/{reflectionId} | Read one |
| [**listReflections**](ReflectionsApi.md#listReflections) | **GET** api/v1/me/reflections | The caller&#39;s reflections, newest first, or those matching a search |
| [**writeReflection**](ReflectionsApi.md#writeReflection) | **POST** api/v1/me/reflections | Write a reflection |



Delete one

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ReflectionsApi::class.java)
val reflectionId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.deleteReflection(reflectionId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **reflectionId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


Edit one

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ReflectionsApi::class.java)
val reflectionId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val writeReflectionRequest : WriteReflectionRequest =  // WriteReflectionRequest | 

launch(Dispatchers.IO) {
    val result : ReflectionResponse = webService.editReflection(reflectionId, writeReflectionRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **reflectionId** | **java.util.UUID**|  | |
| **writeReflectionRequest** | [**WriteReflectionRequest**](WriteReflectionRequest.md)|  | |

### Return type

[**ReflectionResponse**](ReflectionResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Read one

Somebody else&#39;s returns 404, because a 403 would confirm that it exists.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ReflectionsApi::class.java)
val reflectionId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : ReflectionResponse = webService.getReflection(reflectionId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **reflectionId** | **java.util.UUID**|  | |

### Return type

[**ReflectionResponse**](ReflectionResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


The caller&#39;s reflections, newest first, or those matching a search

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ReflectionsApi::class.java)
val q : kotlin.String = q_example // kotlin.String | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<ReflectionResponse> = webService.listReflections(q)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **q** | **kotlin.String**|  | [optional] |

### Return type

[**kotlin.collections.List&lt;ReflectionResponse&gt;**](ReflectionResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Write a reflection

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ReflectionsApi::class.java)
val writeReflectionRequest : WriteReflectionRequest =  // WriteReflectionRequest | 

launch(Dispatchers.IO) {
    val result : ReflectionResponse = webService.writeReflection(writeReflectionRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **writeReflectionRequest** | [**WriteReflectionRequest**](WriteReflectionRequest.md)|  | |

### Return type

[**ReflectionResponse**](ReflectionResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

