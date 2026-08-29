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

Once, by hand, before the pipeline can run anything:

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
  --parameter-overrides WebBaseUrl=https://app.example.org

aws cloudformation deploy --template-file web.yaml \
  --stack-name digital-health-web --capabilities CAPABILITY_IAM \
  --parameter-overrides WebDomainName=app.example.org CertificateArn=arn:aws:acm:us-east-1:...

aws cloudformation deploy --template-file app.yaml \
  --stack-name digital-health-app --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    NetworkStackName=digital-health-network \
    DataStackName=digital-health-data \
    AuthStackName=digital-health-auth \
    ApiDomainName=api.example.org \
    HostedZoneId=Z... \
    WebBaseUrl=https://app.example.org \
    MailFrom=no-reply@example.org \
    ImageTag=<commit sha>
```

The `app` stack fails on its first deploy if no image has been pushed yet: the ECR repository has
to exist before the pipeline can push, and the service cannot start without an image. Deploy `app`
once with `DesiredCount=0`, push an image, then set it back to 1.

## What the pipeline needs

After that first manual round, pushes to `main` redeploy `app` and `web` from
[.github/workflows/ci.yml](../.github/workflows/ci.yml). It authenticates by OIDC, so there is no
AWS key to store — only these repository variables, none of which is a secret:

| Variable | Example |
| --- | --- |
| `AWS_REGION` | `ap-southeast-2` |
| `AWS_DEPLOY_ROLE_ARN` | the `deploy-role` stack's role ARN |
| `API_DOMAIN_NAME` | `api.example.org` |
| `API_CERTIFICATE_ARN` | ACM certificate for the ALB, in `AWS_REGION` |
| `WEB_DOMAIN_NAME` | `app.example.org` |
| `WEB_CERTIFICATE_ARN` | ACM certificate for CloudFront, **in us-east-1** |
| `WEB_BASE_URL` | `https://app.example.org` |
| `HOSTED_ZONE_ID` | the Route 53 zone holding both records |
| `MAIL_FROM` | `no-reply@example.org`, verified in SES |

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

## Checking a change

```bash
cfn-lint infra/*.yaml
```

## Cost

Roughly 70 to 90 US dollars a month: Fargate 0.5 vCPU and 1 GB (~18), ALB (~18), RDS
`db.t4g.micro` (~15), ElastiCache `cache.t4g.micro` (~12), S3 and CloudFront (~5). Cognito is free
below 10,000 monthly active users.
