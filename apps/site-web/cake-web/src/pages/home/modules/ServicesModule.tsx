interface ServiceCard {
  title: string
  description: string
}

interface ServicesModuleProps {
  title: string
  services: ServiceCard[]
}

export function ServicesModule({ title, services }: ServicesModuleProps) {
  return (
    <section className="portal-section">
      <h2>{title}</h2>
      <div className="portal-grid-two">
        {services.map((service) => (
          <article className="portal-card" key={service.title}>
            <h3>{service.title}</h3>
            <p>{service.description}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
