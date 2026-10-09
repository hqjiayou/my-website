from pydantic import BaseModel, Field


class SkillCatalogItem(BaseModel):
    id: str
    description: str = ""
    version: str | None = None


class SkillCatalogResponse(BaseModel):
    skills: list[SkillCatalogItem] = Field(default_factory=list)
