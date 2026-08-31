# OrganisationsApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**archiveOrganisation**](OrganisationsApi.md#archiveOrganisation) | **DELETE** api/v1/orgs/{orgId} | Archive an organisation |
| [**changeOrganisationRole**](OrganisationsApi.md#changeOrganisationRole) | **PATCH** api/v1/orgs/{orgId}/members/{userId} | Change a member&#39;s organisation role |
| [**createOrganisation**](OrganisationsApi.md#createOrganisation) | **POST** api/v1/organisations | Create an organisation |
| [**getOrganisation**](OrganisationsApi.md#getOrganisation) | **GET** api/v1/orgs/{orgId} | Fetch one organisation |
| [**leaveOrganisation**](OrganisationsApi.md#leaveOrganisation) | **DELETE** api/v1/orgs/{orgId}/members/me | Leave an organisation |
| [**listOrganisationMembers**](OrganisationsApi.md#listOrganisationMembers) | **GET** api/v1/orgs/{orgId}/members | List everybody in an organisation |
| [**removeOrganisationMember**](OrganisationsApi.md#removeOrganisationMember) | **DELETE** api/v1/orgs/{orgId}/members/{userId} | Remove a member, ending their team memberships in this organisation |



Archive an organisation

Makes it unreachable for every member while keeping its memberships, teams, and audit history. Nothing is deleted.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OrganisationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.archiveOrganisation(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


Change a member&#39;s organisation role

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OrganisationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val userId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val changeOrgRoleRequest : ChangeOrgRoleRequest =  // ChangeOrgRoleRequest | 

launch(Dispatchers.IO) {
    val result : OrgMemberResponse = webService.changeOrganisationRole(orgId, userId, changeOrgRoleRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **userId** | **java.util.UUID**|  | |
| **changeOrgRoleRequest** | [**ChangeOrgRoleRequest**](ChangeOrgRoleRequest.md)|  | |

### Return type

[**OrgMemberResponse**](OrgMemberResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Create an organisation

The caller becomes its first administrator. Used by the self-signup flow.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OrganisationsApi::class.java)
val createOrganisationRequest : CreateOrganisationRequest =  // CreateOrganisationRequest | 

launch(Dispatchers.IO) {
    val result : OrganisationResponse = webService.createOrganisation(createOrganisationRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **createOrganisationRequest** | [**CreateOrganisationRequest**](CreateOrganisationRequest.md)|  | |

### Return type

[**OrganisationResponse**](OrganisationResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Fetch one organisation

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OrganisationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : OrganisationResponse = webService.getOrganisation(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

[**OrganisationResponse**](OrganisationResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Leave an organisation

Ends the caller&#39;s own membership and their teams within it. The last administrator may leave, which archives the organisation rather than leaving nobody able to administer it.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OrganisationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.leaveOrganisation(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


List everybody in an organisation

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OrganisationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<OrgMemberResponse> = webService.listOrganisationMembers(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

[**kotlin.collections.List&lt;OrgMemberResponse&gt;**](OrgMemberResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Remove a member, ending their team memberships in this organisation

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OrganisationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val userId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.removeOrganisationMember(orgId, userId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **userId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

