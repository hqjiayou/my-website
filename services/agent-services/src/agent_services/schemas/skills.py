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
