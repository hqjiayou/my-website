from pydantic import BaseModel, Field


class SkillCatalogItem(BaseModel):
    id: str
    description: str = ""
    version: str | None = None
    license: str | None = None
    compatibility: str | None = None
    metadata: dict[str, str] = Field(default_factory=dict)
    allowed_tools: list[str] = Field(default_factory=list)


class SkillCatalogResponse(BaseModel):
    skills: list[SkillCatalogItem] = Field(default_factory=list)
    # B 层自选开关的进程级实际状态（AGENT_SKILLS_SELF_SELECT_ENABLED，#195）。
    # 只随目录一并回报供管理面展示；开关本身仍只由进程环境决定，不可经 API 改。
    skills_self_select_enabled: bool = False
