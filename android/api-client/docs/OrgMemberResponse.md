
# OrgMemberResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **email** | **kotlin.String** |  |  [optional] |
| **fullName** | **kotlin.String** |  |  [optional] |
| **joinedAt** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) |  |  [optional] |
| **membershipStatus** | [**inline**](#MembershipStatus) |  |  [optional] |
| **orgRole** | [**inline**](#OrgRole) |  |  [optional] |
| **professionalRole** | **kotlin.String** |  |  [optional] |
| **userId** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |
| **userStatus** | [**inline**](#UserStatus) |  |  [optional] |


<a id="MembershipStatus"></a>
## Enum: membershipStatus
| Name | Value |
| ---- | ----- |
| membershipStatus | ACTIVE, SUSPENDED |


<a id="OrgRole"></a>
## Enum: orgRole
| Name | Value |
| ---- | ----- |
| orgRole | ORG_ADMIN, ORG_MEMBER |


<a id="UserStatus"></a>
## Enum: userStatus
| Name | Value |
| ---- | ----- |
| userStatus | ACTIVE, INVITED, DEACTIVATED |



