# Infrastructure

Five CloudFormation stacks plus a one-off deploy role. Everything is plain CloudFormation — no SAM,
because there are no Lambda functions to package.

| Stack | Template | Holds |
| --- | --- | --- |
| `digital-health-network` | [network.yaml](network.yaml) | VPC, two public subnets, the three security groups |
| `digital-health-data` | [data.yaml](data.yaml) | RDS PostgreSQL, ElastiCache Valkey, the database secret |
| `digital-health-auth` | [auth.yaml](auth.yaml) | Cognito user pool and one app client per platform |
| `digital-health-app` | [app.yaml](app.yaml) | ECR, ECS Fargate service, ALB, ACM certificate, logs |
| `digital-health-web` | [web.yaml](web.yaml) | S3 bucket, CloudFront distribution |
| `digital-health-deploy-role` | [deploy-role.yaml](deploy-role.yaml) | The role GitHub Actions assumes |

## Order

`network` → `data` and `auth` (either order) → `web` → `app`.

`app` needs `web`'s URL for CORS and for the links in invitation emails, and `web` needs nothing
from `app`, so `web` goes first. Stacks are wired by export name, and each consumer takes its
producers' stack names as parameters rather than assuming them.

## Deploying

### First, a domain

Everything below needs one. `app.yaml` has no domainless path: `ApiDomainName` is required and the
load balancer always terminates TLS.

The TLD is a deliverability decision, not just a naming one. Invitation email is how clinicians
reach the product at all, and the cheap TLDs carry enough spam reputation to get filtered on
arrival. Stay with `.com`, `.org`, or `.health`.

This deployment uses **simplicityhelp.com**, registered through Route 53 in account
`917993967729`, which creates the hosted zone as part of registration.

Registering is a purchase with contact details attached, so it is not scripted. The console is the
easier path for it:

```
https://us-east-1.console.aws.amazon.com/route53/domains/home#/DomainSearch
```

Route 53's registration API only exists in `us-east-1`, whatever region the rest of this lives in,
and the console follows the same rule. A domain bought elsewhere works just as well, as long as its
nameservers are delegated to a Route 53 zone in this account.

Watch for the ICANN verification email sent to the registrant address. Unanswered, it suspends the
domain after fifteen days.

### Then everything else

```bash
./infra/bootstrap.sh
```

That covers the whole sequence: it refuses to run against the wrong account, issues the CloudFront
certificate in `us-east-1` and validates it through Route 53, deploys the six stacks in order,
pushes the first image, verifies the sending domain and writes its DKIM records, and sets the
GitHub variables. It is idempotent, so a failed run can simply be repeated.

That gives `app.simplicityhelp.com`, `api.simplicityhelp.com`, and `no-reply@simplicityhelp.com`.
Override `DOMAIN`, `WEB_HOST`, `API_HOST`, or `MAIL_FROM` to change any of them.

The steps it performs, should you want to do them by hand instead:

```bash
aws cloudformation deploy \
  --template-file deploy-role.yaml \
  --stack-name digital-health-deploy-role \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides GitHubOrg=YOUR_ORG GitHubRepository=digital-health-app
```

Then the rest. `network`, `data`, and `auth` change rarely; the pipeline redeploys `app` and `web`
on every push to `main`.

```bash
aws cloudformation deploy --template-file network.yaml \
  --stack-name digital-health-network --capabilities CAPABILITY_IAM

aws cloudformation deploy --template-file data.yaml \
  --stack-name digital-health-data --capabilities CAPABILITY_IAM \
  --parameter-overrides NetworkStackName=digital-health-network

aws cloudformation deploy --template-file auth.yaml \
  --stack-name digital-health-auth --capabilities CAPABILITY_IAM \
  --parameter-overrides WebBaseUrl=https://app.simplicityhelp.com

aws cloudformation deploy --template-file web.yaml \
  --stack-name digital-health-web --capabilities CAPABILITY_IAM \
  --parameter-overrides WebDomainName=app.simplicityhelp.com CertificateArn=arn:aws:acm:us-east-1:...

aws cloudformation deploy --template-file app.yaml \
  --stack-name digital-health-app --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    NetworkStackName=digital-health-network \
    DataStackName=digital-health-data \
    AuthStackName=digital-health-auth \
    ApiDomainName=api.simplicityhelp.com \
    HostedZoneId=Z... \
    WebBaseUrl=https://app.simplicityhelp.com \
    MailFrom=no-reply@simplicityhelp.com \
    ImageTag=<commit sha>
```

The `app` stack fails on its first deploy if no image has been pushed yet: the ECR repository has
to exist before the pipeline can push, and the service cannot start without an image. Deploy `app`
once with `DesiredCount=0`, push an image, then set it back to 1.

The `app` stack issues and validates its own certificate for `ApiDomainName`, which is why
`CertificateArn` is left unset above and why the pipeline never passes it. Supplying that ARN back
as a parameter flips the condition guarding the certificate, and CloudFormation then tries to
delete one its own listener still references. Only CloudFront's certificate is passed in, because
it has to live in `us-east-1` and a stack cannot create a certificate outside its own region.

## What the pipeline needs

After that first manual round, pushes to `main` redeploy `app` and `web` from
[.github/workflows/ci.yml](../.github/workflows/ci.yml). It authenticates by OIDC, so there is no
AWS key to store — only these repository variables, none of which is a secret:

| Variable | Example |
| --- | --- |
| `AWS_REGION` | `ap-southeast-2` |
| `AWS_DEPLOY_ROLE_ARN` | the `deploy-role` stack's role ARN |
| `API_DOMAIN_NAME` | `api.simplicityhelp.com` |
| `WEB_DOMAIN_NAME` | `app.simplicityhelp.com` |
| `WEB_CERTIFICATE_ARN` | ACM certificate for CloudFront, **in us-east-1** |
| `WEB_BASE_URL` | `https://app.simplicityhelp.com` |
| `HOSTED_ZONE_ID` | the Route 53 zone holding both records |
| `MAIL_FROM` | `no-reply@simplicityhelp.com`, verified in SES |

The job runs under a `production` GitHub environment, so approvals and branch restrictions are
configured there rather than in the workflow.

Deploys are keyed on the commit sha rather than `latest`, which is what makes a rollback a matter
of redeploying an older sha. `config.json` is written from stack outputs after the bundle is built,
so the same artefact could be promoted to another environment by writing a different file.

## Things worth knowing before changing any of this

**Public subnets, no NAT Gateway.** A NAT Gateway costs about 32 US dollars a month, roughly a
third of the pilot's whole infrastructure budget. The Fargate tasks are unreachable because their
security group admits nothing but the load balancer, not because of where they sit. Revisit before
this service holds anything more sensitive than de-identified reflections.

**Nothing holds a password.** RDS generates the master password directly into Secrets Manager and
the ECS task definition references the two JSON keys. No credential appears in a template, a
parameter, or the pipeline.

**CloudFront certificates must be in us-east-1**, whichever region the rest of this is deployed to.
The `web` stack takes the ARN as a parameter rather than issuing one, because a stack cannot create
a certificate outside its own region.

**Retention.** The user pool, the S3 bucket, and the database survive stack deletion — `Retain` on
the first two, `Snapshot` on the database. Deleting a stack should never be what loses a
clinician's credentials or their reflections.

## Alarms

The app stack raises a `digital-health-<env>-alarms` SNS topic and six CloudWatch alarms: the API
returning 5xx, having no healthy target, having an unhealthy one for ten minutes, the database low
on storage or busy, and the cache near full.

Alarm descriptions say what a person would notice rather than which metric moved, because the
description is what arrives in the email at an inconvenient hour.

**Set the `ALARM_EMAIL` repository variable, then confirm the subscription.** AWS sends that address
a confirmation link and delivers nothing until somebody clicks it. The stack reports success
regardless, so an unconfirmed subscription looks exactly like a working one:

```bash
aws sns list-subscriptions-by-topic --topic-arn "$(aws cloudformation describe-stacks \
  --stack-name digital-health-app-prod \
  --query "Stacks[0].Outputs[?OutputKey=='AlarmTopicArn'].OutputValue" --output text)" \
  --query 'Subscriptions[*].SubscriptionArn' --output text
```

`PendingConfirmation` there means nobody is being told anything.

With `ALARM_EMAIL` unset the alarms still exist and still record state; they simply have no
subscriber, which is a reasonable interim position but not a monitored one.

To prove the path end to end without waiting for an outage:

```bash
aws cloudwatch set-alarm-state --alarm-name digital-health-prod-api-errors \
  --state-value ALARM --state-reason "testing the notification path"
```

## Checking a change

```bash
cfn-lint infra/*.yaml
```

## Cost

Roughly 65 to 85 US dollars a month: Fargate 0.5 vCPU and 1 GB on Graviton (~14), ALB (~18), RDS
`db.t4g.micro` (~15), ElastiCache `cache.t4g.micro` (~12), S3 and CloudFront (~5). Cognito is free
below 10,000 monthly active users.

Every compute line is arm64. The task definition is Graviton, the pipeline builds on an
`ubuntu-24.04-arm` runner, and `db.t4g` and `cache.t4g` are Graviton too — so nothing is emulated
anywhere and the architecture in production is the one developers run locally.
