# AssistantApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**askAssistant**](AssistantApi.md#askAssistant) | **POST** api/v1/orgs/{orgId}/assistant/questions | Ask a question about the training |



Ask a question about the training

Answers only from published modules in this organisation, with citations. When the material does not cover the question it says so rather than guessing, and no model is called. It never reads reflections and never gives clinical advice.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(AssistantApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val askRequest : AskRequest =  // AskRequest | 

launch(Dispatchers.IO) {
    val result : AnswerResponse = webService.askAssistant(orgId, askRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **askRequest** | [**AskRequest**](AskRequest.md)|  | |

### Return type

[**AnswerResponse**](AnswerResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

