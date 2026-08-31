
# CurrentUserResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **activeOrganisationId** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |
| **email** | **kotlin.String** |  |  [optional] |
| **fullName** | **kotlin.String** |  |  [optional] |
| **id** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |
| **organisations** | [**kotlin.collections.List&lt;OrganisationMembershipResponse&gt;**](OrganisationMembershipResponse.md) |  |  [optional] |
| **phone** | **kotlin.String** |  |  [optional] |
| **platformRole** | [**inline**](#PlatformRole) |  |  [optional] |
| **professionalRole** | **kotlin.String** |  |  [optional] |
| **profileCompleted** | **kotlin.Boolean** |  |  [optional] |
| **status** | [**inline**](#Status) |  |  [optional] |


<a id="PlatformRole"></a>
## Enum: platformRole
| Name | Value |
| ---- | ----- |
| platformRole | SUPER_ADMIN, STANDARD |


<a id="Status"></a>
## Enum: status
| Name | Value |
| ---- | ----- |
| status | ACTIVE, INVITED, DEACTIVATED |



