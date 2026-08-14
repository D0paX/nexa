"""
NEXA Application bootstrap.
"""

import logging
import sys

from nexa import __version__
from nexa.config import get_config


def run() -> int:
    """
    Initializes and starts the NEXA application.
    """
    try:
        config = get_config()
    except Exception as e:
        print(f"Failed to load configuration: {e}", file=sys.stderr)
        return 1

    logging.basicConfig(
        level=config.get("log_level", "INFO"),
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    )
    logger = logging.getLogger(__name__)

    env_name = config.get("env", "development")
    logger.info(f"NEXA version {__version__} starting in {env_name} environment")

    # Phase 0.1 stops here. Phase 1 will implement actual startup behavior.
    return 0
