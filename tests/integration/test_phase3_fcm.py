import asyncio
from typing import Any

import pytest
from httpx import AsyncClient

from nexa.domain.alerts import NotificationDeliveryState
from nexa.notifications.fcm_adapter import FCMAdapter


@pytest.mark.asyncio
async def test_fcm_adapter_semantics(httpserver: Any) -> None:
    """
    Test FCM adapter mapping of HTTP status codes to NotificationDeliveryState.
    """
    project_id = "test-project"

    # 200 -> ACCEPTED
    httpserver.expect_request(
        f"/v1/projects/{project_id}/messages:send", method="POST"
    ).respond_with_data("OK", status=200)

    async with AsyncClient(base_url=httpserver.url_for("/")) as client:
        # Override the adapter url to hit the fake server
        adapter = FCMAdapter(project_id, client)
        adapter.url = httpserver.url_for(f"/v1/projects/{project_id}/messages:send")

        state = await adapter.send_notification({"test": "data"}, "fake-token")
        assert state == NotificationDeliveryState.ACCEPTED

    # Test matrix for RETRYING
    for status in [429, 500, 502, 503]:
        httpserver.clear_all_handlers()
        httpserver.expect_request(
            f"/v1/projects/{project_id}/messages:send", method="POST"
        ).respond_with_data("Error", status=status)

        async with AsyncClient(base_url=httpserver.url_for("/")) as client:
            adapter = FCMAdapter(project_id, client)
            adapter.url = httpserver.url_for(f"/v1/projects/{project_id}/messages:send")
            state = await adapter.send_notification({"test": "data"}, "fake-token")
            assert state == NotificationDeliveryState.RETRYING

    # Test matrix for FAILED
    for status in [400, 401, 403, 404]:
        httpserver.clear_all_handlers()
        httpserver.expect_request(
            f"/v1/projects/{project_id}/messages:send", method="POST"
        ).respond_with_data("Error", status=status)

        async with AsyncClient(base_url=httpserver.url_for("/")) as client:
            adapter = FCMAdapter(project_id, client)
            adapter.url = httpserver.url_for(f"/v1/projects/{project_id}/messages:send")
            state = await adapter.send_notification({"test": "data"}, "fake-token")
            assert state == NotificationDeliveryState.FAILED


@pytest.mark.asyncio
async def test_fcm_adapter_timeout(httpserver: Any) -> None:
    """
    Test that a network timeout results in RETRYING.
    """
    project_id = "test-project"

    async def slow_handler(request: Any) -> Any:
        await asyncio.sleep(2.0)
        return "OK"

    httpserver.expect_request(
        f"/v1/projects/{project_id}/messages:send", method="POST"
    ).respond_with_handler(slow_handler)

    # Configure client to timeout quickly
    async with AsyncClient(base_url=httpserver.url_for("/"), timeout=0.1) as client:
        adapter = FCMAdapter(project_id, client)
        adapter.url = httpserver.url_for(f"/v1/projects/{project_id}/messages:send")
        state = await adapter.send_notification({"test": "data"}, "fake-token")
        assert state == NotificationDeliveryState.RETRYING
