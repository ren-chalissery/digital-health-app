# ModulesApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**archiveModule**](ModulesApi.md#archiveModule) | **DELETE** api/v1/orgs/{orgId}/modules/{moduleId} | Archive a module |
| [**assignModuleToTeams**](ModulesApi.md#assignModuleToTeams) | **PUT** api/v1/orgs/{orgId}/modules/{moduleId}/teams | Set which teams this module is assigned to |
| [**createModule**](ModulesApi.md#createModule) | **POST** api/v1/orgs/{orgId}/modules | Create a module |
| [**getModule**](ModulesApi.md#getModule) | **GET** api/v1/orgs/{orgId}/modules/{moduleId} | One module, with both the published version and the draft |
| [**listModules**](ModulesApi.md#listModules) | **GET** api/v1/orgs/{orgId}/modules | List this organisation&#39;s modules |
| [**openModuleDraft**](ModulesApi.md#openModuleDraft) | **POST** api/v1/orgs/{orgId}/modules/{moduleId}/draft | Open a draft |
| [**publishModule**](ModulesApi.md#publishModule) | **POST** api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish | Publish the draft |
| [**replaceModuleQuiz**](ModulesApi.md#replaceModuleQuiz) | **PUT** api/v1/orgs/{orgId}/modules/{moduleId}/draft/quiz | Replace the draft&#39;s quiz questions |
| [**replaceModuleSections**](ModulesApi.md#replaceModuleSections) | **PUT** api/v1/orgs/{orgId}/modules/{moduleId}/draft/sections | Replace the draft&#39;s sections |
| [**updateModule**](ModulesApi.md#updateModule) | **PATCH** api/v1/orgs/{orgId}/modules/{moduleId} | Rename a module or change its summary |



Archive a module

Hidden from learners and authors alike. Completions and history are kept.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.archiveModule(orgId, moduleId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


Set which teams this module is assigned to

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val assignTeamsRequest : AssignTeamsRequest =  // AssignTeamsRequest | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.assignModuleToTeams(orgId, moduleId, assignTeamsRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |
| **assignTeamsRequest** | [**AssignTeamsRequest**](AssignTeamsRequest.md)|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Create a module

Comes with an empty first draft, since a module with no version cannot be edited.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val createModuleRequest : CreateModuleRequest =  // CreateModuleRequest | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.createModule(orgId, createModuleRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **createModuleRequest** | [**CreateModuleRequest**](CreateModuleRequest.md)|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


One module, with both the published version and the draft

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.getModule(orgId, moduleId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


List this organisation&#39;s modules

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<ModuleSummaryResponse> = webService.listModules(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

[**kotlin.collections.List&lt;ModuleSummaryResponse&gt;**](ModuleSummaryResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Open a draft

Copies what learners currently have, so an edit starts from the live content.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.openModuleDraft(orgId, moduleId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Publish the draft

Set supersedesCompletions when the change is substantive, which sends anyone who completed an earlier version back through it. A corrected typo should not.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val publishRequest : PublishRequest =  // PublishRequest | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.publishModule(orgId, moduleId, publishRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |
| **publishRequest** | [**PublishRequest**](PublishRequest.md)|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Replace the draft&#39;s quiz questions

Each question needs at least two options and exactly one correct one; publishing refuses anything else, since a question with no answer can never be passed.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val replaceQuizRequest : ReplaceQuizRequest =  // ReplaceQuizRequest | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.replaceModuleQuiz(orgId, moduleId, replaceQuizRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |
| **replaceQuizRequest** | [**ReplaceQuizRequest**](ReplaceQuizRequest.md)|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Replace the draft&#39;s sections

Sent whole: editing, reordering, and deleting all happen on one screen.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val replaceSectionsRequest : ReplaceSectionsRequest =  // ReplaceSectionsRequest | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.replaceModuleSections(orgId, moduleId, replaceSectionsRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |
| **replaceSectionsRequest** | [**ReplaceSectionsRequest**](ReplaceSectionsRequest.md)|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Rename a module or change its summary

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(ModulesApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val moduleId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val updateModuleRequest : UpdateModuleRequest =  // UpdateModuleRequest | 

launch(Dispatchers.IO) {
    val result : AuthoredModuleResponse = webService.updateModule(orgId, moduleId, updateModuleRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **moduleId** | **java.util.UUID**|  | |
| **updateModuleRequest** | [**UpdateModuleRequest**](UpdateModuleRequest.md)|  | |

### Return type

[**AuthoredModuleResponse**](AuthoredModuleResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

