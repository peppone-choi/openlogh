"""Shared fixtures for parity tests."""
import os
import time
import pytest
import requests
import pymysql
import psycopg2

# ── Environment ──────────────────────────────────────────────────────────────
LEGACY_BASE = os.environ.get("LEGACY_BASE_URL", "http://legacy-app")
NEW_BASE = os.environ.get("NEW_BASE_URL", "http://new-gateway:8080")
ADMIN_LOGIN_ID = os.environ.get("ADMIN_LOGIN_ID", "admin")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "testadmin123")
PARITY_SCENARIO_CODE = os.environ.get("PARITY_SCENARIO_CODE", "1010")
WORLD_READY_TIMEOUT_SEC = int(os.environ.get("WORLD_READY_TIMEOUT_SEC", "120"))


# ── HTTP Sessions ────────────────────────────────────────────────────────────
class LegacyClient:
    """Wrapper around the legacy PHP API (api.php?path=…)."""

    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.session = requests.Session()

    def call(self, path: str, data: dict | None = None, method: str = "POST") -> requests.Response:
        url = f"{self.base}/api.php?path={path}"
        if method == "GET":
            return self.session.get(url, timeout=30)
        return self.session.post(url, json=data or {}, timeout=30)

    def get(self, path: str, params: dict | None = None) -> requests.Response:
        url = f"{self.base}/api.php?path={path}"
        return self.session.get(url, params=params, timeout=30)


class NewClient:
    """Wrapper around the new Kotlin/Spring API."""

    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.session = requests.Session()
        self.token: str | None = None
        self.world_id: int | None = None

    def _normalize_path(self, path: str) -> str:
        if self.world_id is None:
            return path
        return path.replace("/api/worlds/1", f"/api/worlds/{self.world_id}", 1)

    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    def post(self, path: str, data: dict | None = None) -> requests.Response:
        resolved_path = self._normalize_path(path)
        return self.session.post(
            f"{self.base}{resolved_path}", json=data or {}, headers=self._headers(), timeout=30
        )

    def get(self, path: str, params: dict | None = None) -> requests.Response:
        resolved_path = self._normalize_path(path)
        return self.session.get(
            f"{self.base}{resolved_path}", params=params, headers=self._headers(), timeout=30
        )

    def login(self, login_id: str, password: str):
        r = self.post("/api/auth/login", {"loginId": login_id, "password": password})
        r.raise_for_status()
        self.token = r.json().get("token") or r.json().get("accessToken")
        return r


@pytest.fixture(scope="session")
def legacy() -> LegacyClient:
    return LegacyClient(LEGACY_BASE)


@pytest.fixture(scope="session")
def new() -> NewClient:
    client = NewClient(NEW_BASE)

    try:
        deadline = time.time() + WORLD_READY_TIMEOUT_SEC

        token = None
        while time.time() < deadline:
            try:
                login = requests.post(
                    f"{NEW_BASE}/api/auth/login",
                    json={"loginId": ADMIN_LOGIN_ID, "password": ADMIN_PASSWORD},
                    timeout=30,
                )
                if login.status_code == 200:
                    token = login.json().get("token") or login.json().get("accessToken")
                    if token:
                        break
            except requests.RequestException:
                pass
            time.sleep(1)

        if not token:
            return client

        client.token = token
        headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
        worlds_payload: list[dict] = []
        while time.time() < deadline:
            worlds_resp = requests.get(f"{NEW_BASE}/api/worlds", headers=headers, timeout=30)
            if worlds_resp.status_code == 200:
                payload = worlds_resp.json()
                worlds_payload = payload if isinstance(payload, list) else []
                break
            time.sleep(1)

        worlds = worlds_payload if isinstance(worlds_payload, list) else []
        world_id: int | None = None

        if worlds:
            preferred = next((w for w in worlds if w.get("id") == 1), None)
            selected = preferred or worlds[0]
            world_id = selected.get("id")
        else:
            create = requests.post(
                f"{NEW_BASE}/api/worlds",
                json={"scenarioCode": PARITY_SCENARIO_CODE, "name": "parity-world"},
                headers=headers,
                timeout=30,
            )
            if create.status_code in (200, 201):
                world_id = create.json().get("id")
            elif create.status_code == 409:
                while time.time() < deadline:
                    worlds_retry = requests.get(f"{NEW_BASE}/api/worlds", headers=headers, timeout=30)
                    if worlds_retry.status_code == 200:
                        payload = worlds_retry.json()
                        worlds = payload if isinstance(payload, list) else []
                        if worlds:
                            preferred = next((w for w in worlds if w.get("id") == 1), None)
                            selected = preferred or worlds[0]
                            world_id = selected.get("id")
                            break
                    time.sleep(1)
            else:
                return client

        if world_id is None:
            return client

        client.world_id = int(world_id)

        requests.post(f"{NEW_BASE}/api/worlds/{world_id}/activate", json={}, headers=headers, timeout=30)

        while time.time() < deadline:
            probe = requests.get(f"{NEW_BASE}/api/worlds/{world_id}/history", headers=headers, timeout=30)
            if probe.status_code != 502:
                break
            time.sleep(1)
    except Exception:
        pass

    return client


# ── Database connections ─────────────────────────────────────────────────────
@pytest.fixture(scope="session")
def legacy_db():
    conn = pymysql.connect(
        host=os.environ.get("LEGACY_DB_HOST", "legacy-mariadb"),
        port=int(os.environ.get("LEGACY_DB_PORT", 3306)),
        user=os.environ.get("LEGACY_DB_USER", "root"),
        password=os.environ.get("LEGACY_DB_PASSWORD", "rootpw"),
        database=os.environ.get("LEGACY_DB_NAME", "sammo"),
        cursorclass=pymysql.cursors.DictCursor,
    )
    yield conn
    conn.close()


@pytest.fixture(scope="session")
def new_db():
    conn = psycopg2.connect(
        host=os.environ.get("NEW_DB_HOST", "new-postgres"),
        port=int(os.environ.get("NEW_DB_PORT", 5432)),
        user=os.environ.get("NEW_DB_USER", "opensam"),
        password=os.environ.get("NEW_DB_PASSWORD", "opensam123"),
        dbname=os.environ.get("NEW_DB_NAME", "opensam"),
    )
    conn.autocommit = True
    yield conn
    conn.close()
