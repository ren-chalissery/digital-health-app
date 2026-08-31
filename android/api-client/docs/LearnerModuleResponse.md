
# LearnerModuleResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **completedSectionIds** | [**kotlin.collections.List&lt;java.util.UUID&gt;**](java.util.UUID.md) |  |  [optional] |
| **hasQuiz** | **kotlin.Boolean** |  |  [optional] |
| **moduleId** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |
| **quizPassed** | **kotlin.Boolean** |  |  [optional] |
| **sections** | [**kotlin.collections.List&lt;SectionResponse&gt;**](SectionResponse.md) |  |  [optional] |
| **status** | [**inline**](#Status) |  |  [optional] |
| **summary** | **kotlin.String** |  |  [optional] |
| **title** | **kotlin.String** |  |  [optional] |
| **versionId** | [**java.util.UUID**](java.util.UUID.md) |  |  [optional] |


<a id="Status"></a>
## Enum: status
| Name | Value |
| ---- | ----- |
| status | NOT_STARTED, IN_PROGRESS, COMPLETED, NEEDS_REDOING |



