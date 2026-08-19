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

    return {
        "env": env,
        "log_level": log_level,
        "data_dir": data_dir,
    }
