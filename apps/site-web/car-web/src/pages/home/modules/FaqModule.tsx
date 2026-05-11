interface FaqItem {
  question: string
  answer: string
}

interface FaqModuleProps {
  title: string
  faqs: FaqItem[]
}

export function FaqModule({ title, faqs }: FaqModuleProps) {
  return (
    <section className="portal-section">
      <h2>{title}</h2>
      <div className="faq-list">
        {faqs.map((faq) => (
          <article className="faq-item" key={faq.question}>
            <h3>{faq.question}</h3>
            <p>{faq.answer}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
