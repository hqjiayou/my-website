interface ProductItem {
  title: string
  meta: string
}

interface ProductsModuleProps {
  title: string
  products: ProductItem[]
}

export function ProductsModule({ title, products }: ProductsModuleProps) {
  return (
    <section className="portal-section">
      <h2>{title}</h2>
      <div className="portal-grid-two">
        {products.map((item) => (
          <article className="portal-card" key={item.title}>
            <h3>{item.title}</h3>
            <p>{item.meta}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
