# Phase 2c — Quizzes — Design

Date: 2026-08-29
Status: approved, not yet implemented
Follows: [Phase 2a](2026-08-29-phase2a-modules-and-learning-design.md)

## 1. Scope

A quiz at the end of a module. An author writes single-choice questions with an explanation for
each; a clinician answers them, sees which they got wrong and why, and may retry as often as they
like. A module with a quiz is not complete until every question has been answered correctly.

Video and attachments (2b) remain separate and unstarted.

### Definition of done

An administrator adds questions to a module draft and publishes it. A clinician who has read every
section is offered the quiz, answers it, and sees per-question feedback. The module completes only
once they have every question right, and every attempt along the way is on record.

## 2. Decisions and their rationale

### 2.1 This is mastery learning, not assessment

Unlimited retakes, full feedback, and every question required to pass means everybody passes
eventually. That is the intent: the gate is not "did you already know this" but "have you engaged
with every question until you had it right".

It follows that completion is a weaker claim than it looks, and the product should not later be
read as evidence that a clinician passed an examination. Section 2.3 keeps the information that
would support a stronger claim, should one ever be needed.

### 2.2 Every question carries an explanation

Feedback without explanation is an answer key, and retrying becomes mechanical: read which one was
wrong, pick another. The explanation is where a wrong answer turns into teaching, and it is the
only thing that makes unlimited retakes worth having.

Optional per question, because a question can be obvious enough not to need one, and an author
forced to write filler writes filler.

### 2.3 Every attempt is recorded

Only the passing attempt matters for completion, but "passed on the fourth attempt" is a different
fact from "passed", and it cannot be reconstructed once thrown away. Attempts are cheap: one row
per submission, holding the score and whether it passed.

No reporting is built on this in 2c. The point is that the data exists when somebody asks.

### 2.4 Correct answers never leave the server unasked-for

The learner's view of a quiz returns questions and options with no indication of which option is
right. Marking happens on the server, and correctness comes back only in response to a submitted
attempt.

This is the one part of the feature with a wrong answer that is invisible in the UI: a client that
renders correctly while the payload carries `correct: true` would look perfect and be useless. It
gets a test of its own.

### 2.5 Questions belong to a version

Exactly as sections do. A published quiz is immutable, editing produces a new draft, and an attempt
pins to the version it was taken against. A module whose quiz was rewritten substantively becomes
outstanding again through the machinery 2a already built.

## 3. Schema

Migration `V4__quizzes.sql`:

```sql
CREATE TABLE quiz_question (
    id          UUID PRIMARY KEY,
    version_id  UUID NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    position    INT  NOT NULL,
    prompt      TEXT NOT NULL CHECK (length(trim(prompt)) > 0),
    -- Shown after an attempt. Where a wrong answer becomes teaching rather than an answer key.
    explanation TEXT,
    UNIQUE (version_id, position)
);

CREATE TABLE quiz_option (
    id          UUID PRIMARY KEY,
    question_id UUID    NOT NULL REFERENCES quiz_question (id) ON DELETE CASCADE,
    position    INT     NOT NULL,
    label       TEXT    NOT NULL CHECK (length(trim(label)) > 0),
    correct     BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (question_id, position)
);

CREATE TABLE quiz_attempt (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    version_id     UUID        NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    attempt_number INT         NOT NULL,
    correct_count  INT         NOT NULL,
    question_count INT         NOT NULL,
    passed         BOOLEAN     NOT NULL,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, version_id, attempt_number)
);

CREATE INDEX idx_quiz_attempt_user_version ON quiz_attempt (user_id, version_id);
```

Individual answers are not stored. The score and the outcome are what anyone would ask about, and
keeping which option somebody picked on a failed third attempt is data with no use and a retention
cost.

## 4. API surface

Authoring, `ORG_ADMIN`:

| Method | Path | Effect |
| --- | --- | --- |
| `PUT` | `/api/v1/orgs/{orgId}/modules/{moduleId}/draft/quiz` | Replaces the draft's questions wholesale |

Wholesale, as sections are, and for the same reason: editing, reordering, and deleting arrive
together from one screen.

Publishing validates the quiz: every question needs at least two options and exactly one correct
one. A question with no correct answer can never be passed, and the module would be permanently
uncompletable for everybody it is assigned to — a mistake worth catching at publish rather than
discovering through support.

Learning, any assigned member:

| Method | Path | Effect |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{orgId}/learning/{moduleId}/quiz` | Questions and options, without the answers |
| `POST` | `/api/v1/orgs/{orgId}/learning/{moduleId}/quiz/attempts` | Submits answers, returns the marked result |

The attempt response carries, per question, whether it was right, which option was correct, and the
explanation. Only in response to an attempt.

## 5. Completion

A module with questions completes when every section is read **and** a passing attempt exists for
that version. A module with no questions is unchanged: sections alone finish it.

`LearningService.statusOf` gains the quiz condition, and the completion row is written when the
passing attempt lands rather than when the last section is read. Completion stays derived, which
is what 2a's decision not to store a flag was for.

`AssignedModuleResponse` and `LearnerModuleResponse` gain `hasQuiz` and `quizPassed`, so Learn can
show that sections are done but the quiz is not.

## 6. Client

The module reader shows the quiz below the sections, unlocked once every section is read. Answering
submits the whole set at once; the result shows the score, and each question is marked right or
wrong with its explanation. A retry button clears the selections.

Prompts, options, and explanations render through the same escaping Markdown renderer as section
bodies, so an author cannot inject markup through a question any more than through a section.

## 7. Testing

- The learner's quiz view contains no indication of which option is correct.
- Marking is done server-side: a submission claiming to be right is scored on the server's answers.
- A module with an unpassed quiz stays incomplete even with every section read.
- Passing completes it, and the completion pins to the version attempted.
- Attempts accumulate, and their numbers increase.
- Publishing a quiz with a question that has no correct option is refused.
- Publishing one with a single option is refused.
- A module with no questions still completes on sections alone.

## 8. Out of scope

Question banks, randomised order, partial credit, time limits, per-question scoring weights,
administrator reporting on attempts, and resetting somebody's attempts. The attempt data supports
reporting when it is asked for; nothing reads it yet.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Correct answers leak in the learner payload | A dedicated test asserting the serialised response, not the object |
| A question with no correct answer makes a module uncompletable | Refused at publish, when the author is present to fix it |
| Completion is mistaken for assessment | Named plainly here; attempts are retained so a stronger claim stays possible |
