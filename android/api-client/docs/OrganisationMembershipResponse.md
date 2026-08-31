
# OrganisationMembershipResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **name** | **kotlin.String** |  |  [optional] |
| **orgId** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |
| **orgRole** | [**inline**](#OrgRole) |  |  [optional] |
| **organisationType** | [**inline**](#OrganisationType) |  |  [optional] |
| **slug** | **kotlin.String** |  |  [optional] |
| **teams** | [**kotlin.collections.List&lt;TeamMembershipResponse&gt;**](TeamMembershipResponse.md) |  |  [optional] |


<a id="OrgRole"></a>
## Enum: orgRole
| Name | Value |
| ---- | ----- |
| orgRole | ORG_ADMIN, ORG_MEMBER |


<a id="OrganisationType"></a>
## Enum: organisationType
| Name | Value |
| ---- | ----- |
| organisationType | HOSPITAL, CLINIC, UNIVERSITY, COMPANY, OTHER |



