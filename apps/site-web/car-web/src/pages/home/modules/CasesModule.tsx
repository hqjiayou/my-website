interface CaseItem {
  title: string
  meta: string
}

interface CasesModuleProps {
  title: string
  cases: CaseItem[]
}

export function CasesModule({ title, cases }: CasesModuleProps) {
  return (
    <section className="portal-section">
      <h2>{title}</h2>
      <div className="portal-grid-two">
        {cases.map((caseItem) => (
          <article className="portal-card" key={caseItem.title}>
            <h3>{caseItem.title}</h3>
            <p>{caseItem.meta}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
