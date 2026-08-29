"""Shared plumbing for verifying a deployed environment.

Verification runs against production, because there is no staging environment yet. That makes
cleanup the dangerous part, and this module exists to make one specific mistake impossible:

    NEVER enumerate the user pool and delete what you find.

An earlier run did exactly that and removed a real person's account along with its own. Every
account created here carries a run id, and cleanup deletes only the handful this process made and
holds in memory. If a test account is ever orphaned, it is orphaned under an obvious name and can
be removed deliberately — which is a far better failure than deleting somebody's login.
"""

import os
import uuid

import boto3
import requests
from pycognito import Cognito

API = os.environ.get("API_BASE_URL", "https://api.simplicityhelp.com")
POOL = os.environ["POOL_ID"]
CLIENT = os.environ["CLIENT_ID"]
REGION = os.environ.get("AWS_REGION", "ap-southeast-2")

# Reserved. Nothing a real clinician signs up with will collide with it.
PREFIX = "verify"


class Run:
    """One verification run: the accounts it made, and nothing else."""

    def __init__(self):
        self.id = uuid.uuid4().hex[:8]
        self.password = "Sup3rSecretPass!" + uuid.uuid4().hex[:6]
        self.idp = boto3.client("cognito-idp", region_name=REGION)
        self._created: list[str] = []
        self._organisations: list[tuple[str, dict]] = []
        self.passed: list[str] = []
        self.failed: list[str] = []

    # -- accounts ---------------------------------------------------------------------------

    def account(self, label: str) -> dict:
        """A confirmed, signed-in account whose name marks it as ours."""
        email = f"{PREFIX}-{label}-{self.id}@simplicityhelp.com"
        self.idp.admin_create_user(
            UserPoolId=POOL,
            Username=email,
            MessageAction="SUPPRESS",
            UserAttributes=[
                {"Name": "email", "Value": email},
                {"Name": "email_verified", "Value": "true"},
            ],
        )
        self.idp.admin_set_user_password(
            UserPoolId=POOL, Username=email, Password=self.password, Permanent=True
        )
        self._created.append(email)

        user = Cognito(POOL, CLIENT, username=email)
        user.authenticate(password=self.password)
        headers = {"Authorization": "Bearer " + user.access_token}
        self.call("PUT", "/api/v1/me/profile", headers,
                  {"fullName": label, "professionalRole": "Psychologist"})
        return headers

    def organisation(self, name: str, headers: dict, kind: str = "CLINIC") -> str:
        """An organisation this run owns, archived on cleanup.

        Deleting the Cognito account does not remove what it created, so runs that skipped this
        left organisations and their modules behind in production indefinitely.
        """
        response = self.call("POST", "/api/v1/organisations", headers,
                             {"name": name, "organisationType": kind})
        org_id = response.json()["id"]
        self._organisations.append((org_id, headers))
        return org_id

    def cleanup(self) -> None:
        """Removes only what this run created. Never lists the pool."""
        # Organisations first: archiving needs a live account to authorise it.
        for org_id, headers in self._organisations:
            try:
                self.call("DELETE", f"/api/v1/orgs/{org_id}", headers)
            except Exception:  # noqa: BLE001 - cleanup must not mask a test result
                pass
        self._organisations.clear()

        for email in self._created:
            try:
                self.idp.admin_delete_user(UserPoolId=POOL, Username=email)
            except self.idp.exceptions.UserNotFoundException:
                pass
        self._created.clear()

    # -- requests and assertions ------------------------------------------------------------

    def call(self, method: str, path: str, headers: dict, body=None, timeout: int = 60):
        return requests.request(method, API + path, headers=headers, json=body, timeout=timeout)

    def check(self, name: str, condition: bool, detail: str = "") -> None:
        (self.passed if condition else self.failed).append(name)
        mark = "PASS" if condition else "FAIL"
        print(f"  {mark}  {name}{'  ' + detail if detail else ''}")

    def report(self) -> int:
        print(f"\n{len(self.passed)} passed, {len(self.failed)} failed")
        if self.failed:
            print("failed: " + ", ".join(self.failed))
        return 1 if self.failed else 0
