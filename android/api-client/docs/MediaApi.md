# MediaApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**completeUpload**](MediaApi.md#completeUpload) | **POST** api/v1/orgs/{orgId}/media/{assetId}/uploaded | Report that the upload finished |
| [**deleteMedia**](MediaApi.md#deleteMedia) | **DELETE** api/v1/orgs/{orgId}/media/{assetId} | Delete a video |
| [**listMedia**](MediaApi.md#listMedia) | **GET** api/v1/orgs/{orgId}/media | The organisation&#39;s video library |
| [**registerUpload**](MediaApi.md#registerUpload) | **POST** api/v1/orgs/{orgId}/media | Register a video and get somewhere to put it |
| [**removeCaptions**](MediaApi.md#removeCaptions) | **DELETE** api/v1/orgs/{orgId}/media/{assetId}/captions | Remove the caption track |
| [**setCaptions**](MediaApi.md#setCaptions) | **PUT** api/v1/orgs/{orgId}/media/{assetId}/captions | Attach a WebVTT caption track |



Report that the upload finished

Hands the file to the transcoder; the asset becomes PROCESSING.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(MediaApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val assetId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : MediaAssetResponse = webService.completeUpload(orgId, assetId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **assetId** | **java.util.UUID**|  | |

### Return type

[**MediaAssetResponse**](MediaAssetResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Delete a video

Any section using it keeps its writing and loses the video.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(MediaApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val assetId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.deleteMedia(orgId, assetId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **assetId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


The organisation&#39;s video library

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(MediaApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<MediaAssetResponse> = webService.listMedia(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

[**kotlin.collections.List&lt;MediaAssetResponse&gt;**](MediaAssetResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Register a video and get somewhere to put it

Returns a presigned URL the browser PUTs to directly. Video never passes through the API on the way in.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(MediaApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val registerUploadRequest : RegisterUploadRequest =  // RegisterUploadRequest | 

launch(Dispatchers.IO) {
    val result : UploadTargetResponse = webService.registerUpload(orgId, registerUploadRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **registerUploadRequest** | [**RegisterUploadRequest**](RegisterUploadRequest.md)|  | |

### Return type

[**UploadTargetResponse**](UploadTargetResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Remove the caption track

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(MediaApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val assetId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : MediaAssetResponse = webService.removeCaptions(orgId, assetId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **assetId** | **java.util.UUID**|  | |

### Return type

[**MediaAssetResponse**](MediaAssetResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Attach a WebVTT caption track

Sent as the request body rather than presigned, because a caption file is kilobytes where a video is hundreds of megabytes.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(MediaApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val assetId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val body : kotlin.String = body_example // kotlin.String | 

launch(Dispatchers.IO) {
    val result : MediaAssetResponse = webService.setCaptions(orgId, assetId, body)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **assetId** | **java.util.UUID**|  | |
| **body** | **kotlin.String**|  | |

### Return type

[**MediaAssetResponse**](MediaAssetResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: text/vtt
 - **Accept**: application/json

