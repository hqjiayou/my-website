interface ContactModuleProps {
  title: string
  description: string
  items: string[]
  footerText: string
}

export function ContactModule({ title, description, items, footerText }: ContactModuleProps) {
  return (
    <section className="portal-section contact-module">
      <h2>{title}</h2>
      <p className="contact-description">{description}</p>
      <ul className="contact-list">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <p className="portal-footer">{footerText}</p>
    </section>
  )
}
