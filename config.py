import os
from pathlib import Path
from typing import Any, Optional

from dotenv import load_dotenv
from pydantic import PostgresDsn, field_validator, ValidationInfo
from pydantic_settings import BaseSettings, SettingsConfigDict

# ── Explicitly load .env using absolute path ──────────────────────────────────
# This works on Windows regardless of how the IDE or uvicorn resolves __file__.
# Path(__file__).resolve() gives the absolute path to this file (config.py),
# .parent = app/core/, .parent.parent = app/, .parent.parent.parent = project root
_ENV_FILE = Path(__file__).resolve().parent.parent.parent / ".env"
load_dotenv(dotenv_path=_ENV_FILE, override=False)


class Settings(BaseSettings):
    # ── App ──────────────────────────────────────────────────────────────────
    PROJECT_NAME: str = "dq-backend"
    ENV: str = "development"
    LOG_LEVEL: str = "INFO"
    UPLOAD_DIR: str = "./uploads"

    # ── Database ─────────────────────────────────────────────────────────────
    # No dangerous defaults — if .env is missing these will raise a clear error
    DB_HOST: str
    DB_PORT: int = 5432
    DB_USER: str
    DB_PASSWORD: str
    DB_NAME: str
    DB_POOL_SIZE: int = 5
    DB_MAX_OVERFLOW: int = 10

    SQLALCHEMY_DATABASE_URI: Optional[PostgresDsn] = None

    @field_validator("DB_PORT", mode="before")
    def parse_db_port(cls, v: Any) -> int:
        if v is None or v == "":
            return 5432
        return int(v)

    @field_validator("SQLALCHEMY_DATABASE_URI", mode="before")
    def assemble_db_connection(cls, v: Optional[str], info: ValidationInfo) -> Any:
        if isinstance(v, str) and v:
            return v
        values = info.data
        return PostgresDsn.build(
            scheme="postgresql+asyncpg",
            username=values.get("DB_USER"),
            password=values.get("DB_PASSWORD"),
            host=values.get("DB_HOST"),
            port=values.get("DB_PORT"),
            path=f"{values.get('DB_NAME') or ''}",
        )

    # ── SOGPT ────────────────────────────────────────────────────────────────
    SOGPT_HOST: str = ""
    SOGPT_APP_NAME: str = "DQHUB"
    SOGPT_KEY_NAME: str = "key_2025"
    SOGPT_KEY_VALUE: str = "key_2025"
    SOGPT_MODEL: str = "azure-openai-o3-mini-2025-01-31"

    # ── SGC OAuth ────────────────────────────────────────────────────────────
    SGC_CLIENT_ID: str = ""
    SGC_CLIENT_SECRET: str = ""
    SGC_TOKEN_URL: str = ""
    SCOPE: str = "mail profile openid api.group-06608.v1"

    # ── mTLS ─────────────────────────────────────────────────────────────────
    MTLS_CERT_PATH: str = ""
    MTLS_KEY_PATH: str = ""
    CA_BUNDLE: str = ""

    @property
    def cert_pair(self):
        if self.MTLS_CERT_PATH and self.MTLS_KEY_PATH:
            return (self.MTLS_CERT_PATH, self.MTLS_KEY_PATH)
        return (None, None)

    @property
    def ca_verify(self):
        return self.CA_BUNDLE if self.CA_BUNDLE else True

    model_config = SettingsConfigDict(
        case_sensitive=True,
        env_file=str(_ENV_FILE),      # absolute path — works on Windows
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()

# ── Startup sanity check — print what we actually loaded ─────────────────────
print(f"[config] DB_HOST={settings.DB_HOST}  DB_PORT={settings.DB_PORT}  DB_USER={settings.DB_USER}  DB_NAME={settings.DB_NAME}")
print(f"[config] .env loaded from: {_ENV_FILE}  (exists={_ENV_FILE.exists()})")
