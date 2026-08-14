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

    return {
        "env": env,
        "log_level": log_level,
    }
