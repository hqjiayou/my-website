from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    ok: bool = True
    service: str = "agent-service"
    provider: str
    provider_status: str = Field(alias="providerStatus")
    timestamp: str

    model_config = {"populate_by_name": True}
