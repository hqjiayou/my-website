interface HeroModuleProps {
  templateLabel: string
  title: string
  subtitle: string
  spotlightTitle: string
  spotlightItems: string[]
  primaryAction: string
  secondaryAction: string
}

export function HeroModule({
  templateLabel,
  title,
  subtitle,
  spotlightTitle,
  spotlightItems,
  primaryAction,
  secondaryAction,
}: HeroModuleProps) {
  return (
    <section className="hero-module">
      <div className="hero-content">
        <p className="hero-chip">{templateLabel}</p>
        <h1>{title}</h1>
        <p className="hero-subtitle">{subtitle}</p>
        <div className="hero-actions">
          <button type="button" className="btn-primary">
            {primaryAction}
          </button>
          <button type="button" className="btn-secondary">
            {secondaryAction}
          </button>
        </div>
      </div>

      <aside className="hero-spotlight">
        <h3>{spotlightTitle}</h3>
        <ul>
          {spotlightItems.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </aside>
    </section>
  )
}
