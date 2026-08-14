"""
Tests for configuration loading and validation.
"""

import pytest

from nexa.config import get_config


def test_get_config_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    """Verify defaults are loaded correctly."""
    monkeypatch.delenv("NEXA_ENV", raising=False)
    monkeypatch.delenv("NEXA_LOG_LEVEL", raising=False)

    config = get_config()
    assert config["env"] == "development"
    assert config["log_level"] == "INFO"


def test_get_config_invalid_log_level(monkeypatch: pytest.MonkeyPatch) -> None:
    """Verify invalid log level causes explicit failure."""
    monkeypatch.setenv("NEXA_LOG_LEVEL", "INVALID_LEVEL")

    with pytest.raises(ValueError, match="Invalid log level: INVALID_LEVEL"):
        get_config()


def test_get_config_override(monkeypatch: pytest.MonkeyPatch) -> None:
    """Verify environment variables override defaults."""
    monkeypatch.setenv("NEXA_ENV", "production")
    monkeypatch.setenv("NEXA_LOG_LEVEL", "DEBUG")

    config = get_config()
    assert config["env"] == "production"
    assert config["log_level"] == "DEBUG"
