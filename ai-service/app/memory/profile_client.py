from typing import Any

import httpx

from app.config import Settings


class ProfileClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.local_profiles: dict[int, dict[str, Any]] = {}

    async def get(self, user_id: int, supplied: dict[str, Any] | None = None) -> dict[str, Any]:
        if supplied:
            self.local_profiles[user_id] = supplied
            return supplied
        try:
            async with httpx.AsyncClient(timeout=3) as client:
                response = await client.get(
                    f"{self.settings.java_base_url}/internal/users/{user_id}/profile",
                    headers={"X-Internal-Token": self.settings.java_internal_token},
                )
                response.raise_for_status()
                return response.json()["data"]
        except (httpx.HTTPError, KeyError):
            return self.local_profiles.get(
                user_id,
                {
                    "level": "beginner",
                    "mastery": {},
                    "weak_points": [],
                    "preferences": [],
                    "learning_history": [],
                },
            )

    async def update(self, user_id: int, profile: dict[str, Any]) -> dict[str, Any]:
        self.local_profiles[user_id] = profile
        try:
            async with httpx.AsyncClient(timeout=3) as client:
                await client.put(
                    f"{self.settings.java_base_url}/internal/users/{user_id}/profile",
                    headers={"X-Internal-Token": self.settings.java_internal_token},
                    json=profile,
                )
        except httpx.HTTPError:
            pass
        return profile

