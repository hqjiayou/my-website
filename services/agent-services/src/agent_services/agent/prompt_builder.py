from agent_services.schemas.chat import PromptProfile
from agent_services.schemas.todo import TodoItem


def build_prompt_parts(
    site_id: str,
    user_message: str,
    prompt_profile: PromptProfile | None = None,
    retrieval_context: list[str] | None = None,
    skill_instructions: str | None = None,
    todos: list[TodoItem] | None = None,
) -> tuple[str, str]:
    system = (
        prompt_profile.system_prompt.strip()
        if prompt_profile and prompt_profile.system_prompt and prompt_profile.system_prompt.strip()
        else f"You are the assistant for site: {site_id}. Answer in a concise and helpful way."
    )
    if skill_instructions:
        system = f"{system}\n\n# Active skills\n\n{skill_instructions}"
    if todos:
        lines = "\n".join(f"- [{item.status}] {item.id}: {item.title}" for item in todos)
        system = f"{system}\n\n# Current todos (from trusted context)\n{lines}"
    if retrieval_context:
        knowledge = "\n\n".join(f"- {chunk}" for chunk in retrieval_context)
        system = (
            f"{system}\n\n"
            "Relevant site knowledge (retrieved from the site knowledge base; "
            "prefer it when answering):\n"
            f"{knowledge}"
        )
    return system, user_message.strip()
