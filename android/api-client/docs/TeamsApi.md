# TeamsApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**addTeamMember**](TeamsApi.md#addTeamMember) | **POST** api/v1/orgs/{orgId}/teams/{teamId}/members | Add an organisation member to a team |
| [**createTeam**](TeamsApi.md#createTeam) | **POST** api/v1/orgs/{orgId}/teams | Create a team |
| [**deleteTeam**](TeamsApi.md#deleteTeam) | **DELETE** api/v1/orgs/{orgId}/teams/{teamId} | Delete a team |
| [**getTeam**](TeamsApi.md#getTeam) | **GET** api/v1/orgs/{orgId}/teams/{teamId} | Fetch one team |
| [**listTeamMembers**](TeamsApi.md#listTeamMembers) | **GET** api/v1/orgs/{orgId}/teams/{teamId}/members | List the people in a team |
| [**listTeams**](TeamsApi.md#listTeams) | **GET** api/v1/orgs/{orgId}/teams | List the teams in an organisation |
| [**removeTeamMember**](TeamsApi.md#removeTeamMember) | **DELETE** api/v1/orgs/{orgId}/teams/{teamId}/members/{userId} | Remove somebody from a team |
| [**updateTeam**](TeamsApi.md#updateTeam) | **PATCH** api/v1/orgs/{orgId}/teams/{teamId} | Rename or redescribe a team |



Add an organisation member to a team

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val teamId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val addTeamMemberRequest : AddTeamMemberRequest =  // AddTeamMemberRequest | 

launch(Dispatchers.IO) {
    val result : TeamMemberDetailResponse = webService.addTeamMember(orgId, teamId, addTeamMemberRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **teamId** | **java.util.UUID**|  | |
| **addTeamMemberRequest** | [**AddTeamMemberRequest**](AddTeamMemberRequest.md)|  | |

### Return type

[**TeamMemberDetailResponse**](TeamMemberDetailResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Create a team

Restricted to organisation administrators; team administrators cannot.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val createTeamRequest : CreateTeamRequest =  // CreateTeamRequest | 

launch(Dispatchers.IO) {
    val result : TeamResponse = webService.createTeam(orgId, createTeamRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **createTeamRequest** | [**CreateTeamRequest**](CreateTeamRequest.md)|  | |

### Return type

[**TeamResponse**](TeamResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Delete a team

Organisation administrators only. A team administrator can manage their team&#39;s membership but cannot remove the team itself.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val teamId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.deleteTeam(orgId, teamId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **teamId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


Fetch one team

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val teamId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : TeamResponse = webService.getTeam(orgId, teamId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **teamId** | **java.util.UUID**|  | |

### Return type

[**TeamResponse**](TeamResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


List the people in a team

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val teamId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<TeamMemberDetailResponse> = webService.listTeamMembers(orgId, teamId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **teamId** | **java.util.UUID**|  | |

### Return type

[**kotlin.collections.List&lt;TeamMemberDetailResponse&gt;**](TeamMemberDetailResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


List the teams in an organisation

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<TeamResponse> = webService.listTeams(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

[**kotlin.collections.List&lt;TeamResponse&gt;**](TeamResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Remove somebody from a team

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val teamId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val userId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.removeTeamMember(orgId, teamId, userId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **teamId** | **java.util.UUID**|  | |
| **userId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


Rename or redescribe a team

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(TeamsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val teamId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val updateTeamRequest : UpdateTeamRequest =  // UpdateTeamRequest | 

launch(Dispatchers.IO) {
    val result : TeamResponse = webService.updateTeam(orgId, teamId, updateTeamRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **teamId** | **java.util.UUID**|  | |
| **updateTeamRequest** | [**UpdateTeamRequest**](UpdateTeamRequest.md)|  | |

### Return type

[**TeamResponse**](TeamResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

