
# InvitationResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **createdAt** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) |  |  [optional] |
| **email** | **kotlin.String** |  |  [optional] |
| **expiresAt** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) |  |  [optional] |
| **id** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |
| **orgRole** | [**inline**](#OrgRole) |  |  [optional] |
| **status** | [**inline**](#Status) |  |  [optional] |
| **teamId** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |
| **teamName** | **kotlin.String** |  |  [optional] |
| **teamRole** | [**inline**](#TeamRole) |  |  [optional] |


<a id="OrgRole"></a>
## Enum: orgRole
| Name | Value |
| ---- | ----- |
| orgRole | ORG_ADMIN, ORG_MEMBER |


<a id="Status"></a>
## Enum: status
| Name | Value |
| ---- | ----- |
| status | PENDING, ACCEPTED, REVOKED, EXPIRED |


<a id="TeamRole"></a>
## Enum: teamRole
| Name | Value |
| ---- | ----- |
| teamRole | TEAM_ADMIN, TEAM_MEMBER |



