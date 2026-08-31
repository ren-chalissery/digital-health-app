# LearningApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**completeSection**](LearningApi.md#completeSection) | **PUT** api/v1/orgs/{orgId}/learning/sections/{sectionId}/complete | Mark a section as read |
| [**getPlaybackUrl**](LearningApi.md#getPlaybackUrl) | **GET** api/v1/orgs/{orgId}/learning/media/{assetId}/playback | A short-lived URL for a video in an assigned module |
| [**getQuiz**](LearningApi.md#getQuiz) | **GET** api/v1/orgs/{orgId}/learning/{moduleId}/quiz | The quiz for an assigned module |
| [**listAssignedModules**](LearningApi.md#listAssignedModules) | **GET** api/v1/orgs/{orgId}/learning | Modules assigned to the caller&#39;s teams, with their progress |
| [**readModule**](LearningApi.md#readModule) | **GET** api/v1/orgs/{orgId}/learning/{moduleId} | The published version of one assigned module |
| [**submitQuizAttempt**](LearningApi.md#submitQuizAttempt) | **POST** api/v1/orgs/{orgId}/learning/{moduleId}/quiz/attempts | Answer the quiz |



Mark a section as read

Completing the last section completes the module in the same transaction.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(LearningApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val sectionId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : LearnerModuleResponse = webService.completeSection(orgId, sectionId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **sectionId** | **java.util.UUID**|  | |

### Return type

[**LearnerModuleResponse**](LearnerModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


A short-lived URL for a video in an assigned module

Minted per request after the same assignment check that guards the module. Holding an asset id is not authorisation.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(LearningApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val assetId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : PlaybackResponse = webService.getPlaybackUrl(orgId, assetId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **assetId** | **java.util.UUID**|  | |

### Return type

[**PlaybackResponse**](PlaybackResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


The quiz for an assigned module

Questions and options only. Which option is correct is never sent here.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(LearningApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : QuizResponse = webService.getQuiz(orgId, moduleId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |

### Return type

[**QuizResponse**](QuizResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Modules assigned to the caller&#39;s teams, with their progress

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(LearningApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<AssignedModuleResponse> = webService.listAssignedModules(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

[**kotlin.collections.List&lt;AssignedModuleResponse&gt;**](AssignedModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


The published version of one assigned module

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(LearningApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : LearnerModuleResponse = webService.readModule(orgId, moduleId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |

### Return type

[**LearnerModuleResponse**](LearnerModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Answer the quiz

Marked on the server. Returns which questions were right, the correct answer, and the author&#39;s explanation. Passing completes the module if every section is read.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(LearningApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val submitAttemptRequest : SubmitAttemptRequest =  // SubmitAttemptRequest | 

launch(Dispatchers.IO) {
    val result : AttemptResultResponse = webService.submitQuizAttempt(orgId, moduleId, submitAttemptRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |
| **submitAttemptRequest** | [**SubmitAttemptRequest**](SubmitAttemptRequest.md)|  | |

### Return type

[**AttemptResultResponse**](AttemptResultResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

