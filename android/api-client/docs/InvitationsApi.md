# InvitationsApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**acceptInvitation**](InvitationsApi.md#acceptInvitation) | **POST** api/v1/invitations/{token}/accept | Accept an invitation as the signed-in user |
| [**createInvitation**](InvitationsApi.md#createInvitation) | **POST** api/v1/orgs/{orgId}/invitations | Invite somebody to the organisation |
| [**listInvitations**](InvitationsApi.md#listInvitations) | **GET** api/v1/orgs/{orgId}/invitations | List an organisation&#39;s invitations |
| [**previewInvitation**](InvitationsApi.md#previewInvitation) | **GET** api/v1/invitations/{token} | Preview an invitation before signing up |
| [**revokeInvitation**](InvitationsApi.md#revokeInvitation) | **DELETE** api/v1/orgs/{orgId}/invitations/{invitationId} | Withdraw an outstanding invitation |



Accept an invitation as the signed-in user

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(InvitationsApi::class.java)
val token : kotlin.String = token_example // kotlin.String | 

launch(Dispatchers.IO) {
    webService.acceptInvitation(token)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **token** | **kotlin.String**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


Invite somebody to the organisation

Re-inviting an address withdraws the outstanding invitation and issues a fresh link, so only one token is ever live for a given address.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(InvitationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val createInvitationRequest : CreateInvitationRequest =  // CreateInvitationRequest | 

launch(Dispatchers.IO) {
    val result : InvitationResponse = webService.createInvitation(orgId, createInvitationRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **createInvitationRequest** | [**CreateInvitationRequest**](CreateInvitationRequest.md)|  | |

### Return type

[**InvitationResponse**](InvitationResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


List an organisation&#39;s invitations

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(InvitationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    val result : kotlin.collections.List<InvitationResponse> = webService.listInvitations(orgId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |

### Return type

[**kotlin.collections.List&lt;InvitationResponse&gt;**](InvitationResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Preview an invitation before signing up

Public. Returns valid&#x3D;false for anything unknown, expired, or already used, so the endpoint cannot be used to probe for live tokens.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(InvitationsApi::class.java)
val token : kotlin.String = token_example // kotlin.String | 

launch(Dispatchers.IO) {
    val result : InvitationPreviewResponse = webService.previewInvitation(token)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **token** | **kotlin.String**|  | |

### Return type

[**InvitationPreviewResponse**](InvitationPreviewResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Withdraw an outstanding invitation

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(InvitationsApi::class.java)
val orgId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 
val invitationId : java.util.UUID = 38400000-8cf0-11bd-b23e-10b96e4ef00d // java.util.UUID | 

launch(Dispatchers.IO) {
    webService.revokeInvitation(orgId, invitationId)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **orgId** | **java.util.UUID**|  | |
| **invitationId** | **java.util.UUID**|  | |

### Return type

null (empty response body)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

