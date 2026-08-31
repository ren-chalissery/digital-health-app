# CurrentUserApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getCurrentUser**](CurrentUserApi.md#getCurrentUser) | **GET** api/v1/me | Describe the signed-in user |
| [**setActiveOrganisation**](CurrentUserApi.md#setActiveOrganisation) | **PUT** api/v1/me/active-organisation | Choose which organisation to work in |
| [**updateProfile**](CurrentUserApi.md#updateProfile) | **PUT** api/v1/me/profile | Complete or update the professional profile |



Describe the signed-in user

Provisions the user on first call. Clients use profileCompleted and the organisations list to decide whether to show onboarding.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(CurrentUserApi::class.java)

launch(Dispatchers.IO) {
    val result : CurrentUserResponse = webService.getCurrentUser()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**CurrentUserResponse**](CurrentUserResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Choose which organisation to work in

Stored on the user so the choice follows them to another device. Refused for any organisation that is not a live membership of the caller&#39;s.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(CurrentUserApi::class.java)
val setActiveOrganisationRequest : SetActiveOrganisationRequest =  // SetActiveOrganisationRequest | 

launch(Dispatchers.IO) {
    val result : CurrentUserResponse = webService.setActiveOrganisation(setActiveOrganisationRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **setActiveOrganisationRequest** | [**SetActiveOrganisationRequest**](SetActiveOrganisationRequest.md)|  | |

### Return type

[**CurrentUserResponse**](CurrentUserResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Complete or update the professional profile

Sets profileCompleted, which is what lets the client leave the wizard.

### Example
```kotlin
// Import classes:
//import io.simplicity.training.api.*
//import io.simplicity.training.api.infrastructure.*
//import io.simplicity.training.api.models.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(CurrentUserApi::class.java)
val updateProfileRequest : UpdateProfileRequest =  // UpdateProfileRequest | 

launch(Dispatchers.IO) {
    val result : CurrentUserResponse = webService.updateProfile(updateProfileRequest)
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **updateProfileRequest** | [**UpdateProfileRequest**](UpdateProfileRequest.md)|  | |

### Return type

[**CurrentUserResponse**](CurrentUserResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

