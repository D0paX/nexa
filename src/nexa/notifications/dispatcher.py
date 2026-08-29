import logging
from datetime import datetime, timedelta

import google.auth
from google.auth.transport.requests import Request as SyncRequest
from google.oauth2 import service_account

from nexa.domain.alerts import NotificationDeliveryState
from nexa.notifications.fcm_adapter import FCMAdapter
from nexa.persistence.sqlite_alerts import SqliteAlertRepository

logger = logging.getLogger(__name__)


class NotificationDispatcher:
    """
    Asynchronously reads pending notifications from the repository and
    dispatches them via FCM.
    """

    def __init__(
        self,
        alert_repo: SqliteAlertRepository,
        fcm_adapter: FCMAdapter,
        credentials_path: str = "",
    ):
        self.alert_repo = alert_repo
        self.fcm_adapter = fcm_adapter
        self.credentials_path = credentials_path
        self._credentials = None

        self._init_credentials()

    def _init_credentials(self) -> None:
        """Initialize Google Auth credentials."""
        scopes = ["https://www.googleapis.com/auth/firebase.messaging"]
        try:
            if self.credentials_path:
                self._credentials = (
                    service_account.Credentials.from_service_account_file(  # type: ignore
                        self.credentials_path, scopes=scopes
                    )
                )
            else:
                self._credentials, _ = google.auth.default(scopes=scopes)  # type: ignore
        except Exception as e:
            logger.error(f"Failed to load Google Auth credentials: {e}")
            self._credentials = None

    def _get_access_token(self) -> str:
        """Retrieves and refreshes the access token if needed."""
        if not self._credentials:
            return ""
        if not self._credentials.valid:
            request = SyncRequest()
            self._credentials.refresh(request)
        return self._credentials.token

    async def dispatch_pending(self, limit: int = 50) -> None:
        """
        Fetches QUEUED or RETRYING notifications whose next_retry_at is <= now,
        and dispatches them.
        """
        notifications = self.alert_repo.get_pending_notifications(limit=limit)
        if not notifications:
            return

        access_token = self._get_access_token()
        if not access_token:
            logger.error("No valid access token available, skipping dispatch.")
            return

        for notif in notifications:
            notif.state = NotificationDeliveryState.IN_FLIGHT
            self.alert_repo.update_notification(notif)

            new_state = await self.fcm_adapter.send_notification(
                notif.payload, access_token
            )

            notif.state = new_state

            if new_state == NotificationDeliveryState.RETRYING:
                notif.retry_count += 1
                if notif.retry_count > 5:
                    notif.state = NotificationDeliveryState.EXHAUSTED
                else:
                    # Exponential backoff
                    delay = 2**notif.retry_count
                    notif.next_retry_at = datetime.utcnow() + timedelta(seconds=delay)

            notif.updated_at = datetime.utcnow()
            self.alert_repo.update_notification(notif)
