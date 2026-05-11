import type { Locale } from '../../i18n/locale'
import { LOCALE_OPTIONS } from '../../i18n/locale'
import type { ThemeMode } from '../../theme/theme'
import { siteHomeLocales } from './locales'
import { CasesModule } from './modules/CasesModule'
import { ContactModule } from './modules/ContactModule'
import { FaqModule } from './modules/FaqModule'
import { HeroModule } from './modules/HeroModule'
import { PortalLinksModule } from './modules/PortalLinksModule'
import { ServicesModule } from './modules/ServicesModule'

interface HomePageProps {
  locale: Locale
  onLocaleChange: (locale: Locale) => void
  theme: ThemeMode
  onThemeChange: (theme: ThemeMode) => void
}

export function HomePage({ locale, onLocaleChange, theme, onThemeChange }: HomePageProps) {
  const copy = siteHomeLocales[locale]

  return (
    <div className="site-page">
      <header className="portal-toolbar">
        <p className="site-name">{copy.siteName}</p>
        <div className="toolbar-controls">
          <label className="control-field">
            <span>{copy.languageLabel}</span>
            <select value={locale} onChange={(event) => onLocaleChange(event.target.value as Locale)}>
              {LOCALE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="control-field">
            <span>{copy.themeLabel}</span>
            <select value={theme} onChange={(event) => onThemeChange(event.target.value as ThemeMode)}>
              <option value="light">{copy.lightThemeLabel}</option>
              <option value="dark">{copy.darkThemeLabel}</option>
            </select>
          </label>
        </div>
      </header>

      <HeroModule
        templateLabel={copy.templateLabel}
        title={copy.title}
        subtitle={copy.subtitle}
        spotlightTitle={copy.spotlightTitle}
        spotlightItems={copy.spotlightItems}
        primaryAction={copy.primaryAction}
        secondaryAction={copy.secondaryAction}
      />

      <main className="portal-main">
        <PortalLinksModule title={copy.portalNavTitle} links={copy.portalLinks} />
        <ServicesModule title={copy.servicesTitle} services={copy.services} />
        <CasesModule title={copy.casesTitle} cases={copy.cases} />
        <FaqModule title={copy.faqTitle} faqs={copy.faqs} />
        <ContactModule
          title={copy.contactTitle}
          description={copy.contactDescription}
          items={copy.contactItems}
          footerText={copy.footerText}
        />
      </main>
    </div>
  )
}
