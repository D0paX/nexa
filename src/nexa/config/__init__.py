"""
Configuration module.

Responsible for loading and validating application configuration.
"""

import os
from typing import Any, Dict


def get_config() -> Dict[str, Any]:
    """
    Loads configuration from environment.
    """
    env = os.environ.get("NEXA_ENV", "development")
    log_level = os.environ.get("NEXA_LOG_LEVEL", "INFO")

    # Fail clearly on invalid log level
    if log_level not in ("DEBUG", "INFO", "WARNING", "ERROR"):
        raise ValueError(f"Invalid log level: {log_level}")

    # Handle explicit NEXA_DATA_DIR or default to ~/.nexa/data
    data_dir = os.environ.get("NEXA_DATA_DIR")
    if not data_dir:
        # Default to ~/.nexa/data. If ~ is unresolvable, use ./.nexa/data
        user_home = os.path.expanduser("~")
        if user_home == "~":
            data_dir = os.path.abspath(os.path.join(".", ".nexa", "data"))
        else:
            data_dir = os.path.abspath(os.path.join(user_home, ".nexa", "data"))

    fcm_project_id = os.environ.get("FCM_PROJECT_ID", "")
    fcm_credentials_path = os.environ.get("FCM_CREDENTIALS_PATH", "")
    try:
        max_outbox_high_water_mark = int(
            os.environ.get("MAX_OUTBOX_HIGH_WATER_MARK", "1000")
        )
    except ValueError:
        max_outbox_high_water_mark = 1000

    # Phase 4 Enforcement settings
    enforcement_enabled = (
        os.environ.get("ENFORCEMENT_ENABLED", "false").lower() == "true"
    )
    max_concurrent_actions = int(os.environ.get("MAX_CONCURRENT_ACTIONS", "5"))
    action_timeout = int(os.environ.get("ACTION_TIMEOUT", "5"))
    rollback_timeout = int(os.environ.get("ROLLBACK_TIMEOUT", "5"))
    max_retries = int(os.environ.get("MAX_RETRIES", "3"))

    return {
        "env": env,
        "log_level": log_level,
        "data_dir": data_dir,
        "fcm_project_id": fcm_project_id,
        "fcm_credentials_path": fcm_credentials_path,
        "max_outbox_high_water_mark": max_outbox_high_water_mark,
        "enforcement_enabled": enforcement_enabled,
        "max_concurrent_actions": max_concurrent_actions,
        "action_timeout": action_timeout,
        "rollback_timeout": rollback_timeout,
        "max_retries": max_retries,
    }
