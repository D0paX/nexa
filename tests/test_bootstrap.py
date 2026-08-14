"""
Tests for foundational application bootstrap.
"""

import importlib

import pytest

from nexa import __version__


def test_version_deterministic() -> None:
    """Verify the version string is defined and correctly formatted."""
    assert isinstance(__version__, str)
    assert __version__ == "0.1.0"


def test_module_imports() -> None:
    """Verify that core modules can be imported without errors."""
    core_modules = [
        "nexa",
        "nexa.config",
        "nexa.domain",
        "nexa.events",
        "nexa.identity",
        "nexa.network",
        "nexa.notifications",
        "nexa.observability",
        "nexa.persistence",
    ]
    for module_name in core_modules:
        module = importlib.import_module(module_name)
        assert module is not None


def test_app_bootstrap(monkeypatch: pytest.MonkeyPatch) -> None:
    """Verify that the app can run its bootstrap function."""
    # Ensure it doesn't actually run anything stateful
    from nexa.app import run

    # Mock environment variables to ensure safe start
    monkeypatch.setenv("NEXA_ENV", "test")

    exit_code = run()
    assert exit_code == 0
