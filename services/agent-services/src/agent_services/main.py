import uvicorn

from agent_services.config import get_settings


def run() -> None:
    settings = get_settings()
    uvicorn.run(
        "agent_services.app:create_app",
        factory=True,
        host="0.0.0.0",
        port=settings.port,
        workers=settings.uvicorn_workers if settings.uvicorn_workers > 1 else None,
        log_level="info",
    )


if __name__ == "__main__":
    run()
