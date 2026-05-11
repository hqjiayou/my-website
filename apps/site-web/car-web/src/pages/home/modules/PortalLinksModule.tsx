interface PortalLink {
  title: string
  description: string
}

interface PortalLinksModuleProps {
  title: string
  links: PortalLink[]
}

export function PortalLinksModule({ title, links }: PortalLinksModuleProps) {
  return (
    <section className="portal-section">
      <h2>{title}</h2>
      <div className="portal-link-grid">
        {links.map((link) => (
          <article className="portal-card" key={link.title}>
            <h3>{link.title}</h3>
            <p>{link.description}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
