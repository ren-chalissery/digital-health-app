# SES production access

The account is in the SES sandbox in `ap-southeast-2`. Sandbox sending only reaches addresses
verified by hand, so an invitation to a real clinician fails rather than arrives. This is the last
thing standing between the invitation flow and actual use.

```bash
aws sesv2 get-account --query ProductionAccessEnabled   # false
```

Sandbox also caps sending at 200 messages a day and one a second, against an application limit of
50 invitations per hour per organisation. Two active organisations can exceed the daily cap, so
verifying recipients by hand is a way to keep testing, not a way to run.

The sending domain is not the problem and needs nothing done to it: `simplicityhelp.com` is verified
with DKIM signing enabled.

## Before submitting

Deploy the bounce and complaint handling first. The question below about handling bounces is the one
these requests are most often rejected on, and the answer should describe what is running rather
than what is planned. Run `./infra/bootstrap-mail.sh` to stand up the configuration set, the
`mail-events` topic and the two reputation alarms without the managed app stack; the app stack
creates the same resources when it is deployed.

Submit through the console rather than `aws sesv2 put-account-details`. The CLI call submits
immediately with no chance to review, and the request cannot be edited afterwards.

    https://ap-southeast-2.console.aws.amazon.com/ses/home?region=ap-southeast-2#/account

## The request

**Mail type:** Transactional

**Website URL:** `https://app.simplicityhelp.com`

**Use case description:**

> Simplicity is a clinical training platform used by hospitals and clinics to deliver compulsory
> training modules to their staff. The only email the application sends is a one-to-one invitation,
> and it is only ever sent in direct response to an administrator at a customer organisation
> entering a named colleague's address to grant them access to that organisation's training.
>
> There are no mailing lists, no marketing, no newsletters, and no bulk or automated sends.
> Addresses are never purchased, imported, scraped, or shared between organisations. Each message
> goes to a single named recipient, is triggered synchronously by an authenticated administrator
> action, and contains one expiring link to accept the invitation. Links expire after 7 days.
>
> The application enforces its own ceiling of 50 invitations per hour per organisation and refuses
> sends above it. Every invitation is recorded in an audit trail against the administrator who
> issued it. The sending domain, simplicityhelp.com, is verified with DKIM signing enabled, and mail
> is sent only from no-reply@simplicityhelp.com.
>
> Recipients who do not want the invitation can ignore it: the link expires by itself and no further
> email is sent. There is no recurring or follow-up mail of any kind, so there is nothing to
> unsubscribe from.

**How you will handle bounces and complaints:**

> All mail is sent under a SES configuration set, which publishes bounce, complaint, rejection and
> rendering-failure events to an SNS topic monitored by the operations address. CloudWatch alarms on
> the account's bounce rate and complaint rate notify the same address at 5 per cent and 0.1 per
> cent respectively, below the thresholds at which AWS reviews a sender.
>
> Because every message is an invitation to a named individual entered by an administrator, a bounce
> almost always means a mistyped address. The invitation expires on its own and the administrator
> re-enters the address. Repeated bounces or any complaint from a single organisation are
> investigated against the audit trail, which records the administrator behind every invitation.

**Additional contacts:** the `ALARM_EMAIL` operations address.

**Expected daily sending volume:** *to be filled in before submitting.* Frame it against the
application's own ceiling, for example: "Fewer than N a day across N organisations, bounded by an
application-enforced limit of 50 invitations per hour per organisation."

## Afterwards

```bash
aws sesv2 get-account --query ProductionAccessEnabled   # true
```

Approval usually takes about a day. Nothing needs redeploying — the sandbox restriction is an
account property, so invitations to unverified addresses start working as soon as it lifts. The
addresses verified by hand for testing can then be removed:

```bash
aws sesv2 list-email-identities --query "EmailIdentities[?IdentityType=='EMAIL_ADDRESS'].IdentityName"
```
