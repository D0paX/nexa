import logging
from typing import Any, Dict

import httpx

from nexa.domain.alerts import NotificationDeliveryState

logger = logging.getLogger(__name__)


class FCMAdapter:
    """
    HTTP v1 adapter for Firebase Cloud Messaging.
    """

    def __init__(self, project_id: str, client: httpx.AsyncClient):
        self.project_id = project_id
        self.client = client
        self.url = (
            f"https://fcm.googleapis.com/v1/projects/{self.project_id}/messages:send"
        )

    async def send_notification(
        self, payload: Dict[str, Any], access_token: str
    ) -> NotificationDeliveryState:
        """
        Sends a notification via FCM HTTP v1 API.
        Takes the raw alert payload and maps FCM response codes to
        NotificationDeliveryState.
        """
        fcm_payload = {"message": {"topic": "security_alerts", "data": payload}}

        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json",
        }

        try:
            response = await self.client.post(
                self.url, json=fcm_payload, headers=headers
            )
        except (httpx.TimeoutException, httpx.NetworkError) as e:
            logger.warning(f"FCM Network error: {e}")
            return NotificationDeliveryState.RETRYING

        if response.status_code == 200:
            return NotificationDeliveryState.ACCEPTED
        elif response.status_code in (400, 401, 403, 404):
            logger.error(
                f"FCM Error (permanent): {response.status_code} - {response.text}"
            )
            return NotificationDeliveryState.FAILED
        elif response.status_code in (429, 500, 502, 503):
            logger.warning(
                f"FCM Error (transient): {response.status_code} - {response.text}"
            )
            return NotificationDeliveryState.RETRYING
        else:
            logger.error(
                f"FCM Error (unknown): {response.status_code} - {response.text}"
            )
            return NotificationDeliveryState.FAILED
