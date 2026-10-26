import re

from agent_services.agent.skills import limit_skill_instructions
from agent_services.schemas.chat import PromptProfile
from agent_services.schemas.todo import TodoItem

# 只接受语言标签，避免把任意文本写进 system prompt
_LOCALE_RE = re.compile(r"^[A-Za-z]{2,3}(?:[_-][A-Za-z0-9]{2,8}){0,3}$")


def build_prompt_parts(
    site_id: str,
    user_message: str,
    prompt_profile: PromptProfile | None = None,
    retrieval_context: list[str] | None = None,
    skill_instructions: str | None = None,
    todos: list[TodoItem] | None = None,
    locale: str | None = None,
    skills_max_instruction_chars: int | None = None,
    knowledge_available: bool = False,
) -> tuple[str, str]:
    system = (
        prompt_profile.system_prompt.strip()
        if prompt_profile and prompt_profile.system_prompt and prompt_profile.system_prompt.strip()
        else f"You are the assistant for site: {site_id}. Answer in a concise and helpful way."
    )
    # 事实锚定（常开）：关于本站的陈述只能来自工具结果与既有上下文；
    # category 只是组织用标签、不是行业定性；工具没覆盖的就说不可用，禁止臆造站点业务与身份。
    system = (
        f"{system}\n\n"
        "Describe this site only from tool results and the provided context. "
        "The site's category is just an organizational tag, not a business classification. "
        "If no tool data covers something, say it is not available instead of guessing."
    )
    if knowledge_available:
        # 只在本轮确实注册了 search_site_knowledge 时才写，避免提示与工具不一致
        system = (
            f"{system}\n\n"
            "When the user asks about this site's documents or facts, use the "
            "search_site_knowledge tool first and answer from the returned passages; "
            "if the search returns nothing relevant, say you could not find it."
        )
    if skill_instructions:
        text = skill_instructions
        if skills_max_instruction_chars is not None:
            text = limit_skill_instructions(text, skills_max_instruction_chars)
        if text:
            system = f"{system}\n\n# Active skills\n\n{text}"
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
    if locale and _LOCALE_RE.fullmatch(locale.strip()):
        system = f"{system}\n\nReply in this locale: {locale.strip()}."
    return system, user_message.strip()
