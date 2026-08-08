export default {
  title: 'Style Settings',
  description: 'Edit the site appearance (schema v1: primary color, font, button, hero layout).',
  currentSite: 'Current site',
  saveConfig: 'Save',
  saving: 'Saving…',
  resetDefault: 'Reset to loaded',
  previewSite: 'Preview',
  editorTitle: 'Appearance fields',
  versionLabel: 'Version',
  fields: {
    primaryColor: 'Primary color',
    fontStyle: 'Font style',
    buttonStyle: 'Button style',
    heroLayout: 'Hero layout',
  },
  previewMode: {
    desktop: 'Desktop',
    mobile: 'Mobile',
  },
  preview: {
    headerMenu: 'Menu',
    heroTitle: 'Hero title',
    heroSubtitle: 'Preview hero and button styles from schema v1.',
    primaryAction: 'Contact us',
  },
  options: {
    fontStyle: {
      modern: 'Modern',
      classic: 'Classic',
      soft: 'Soft',
      japanese: 'Japanese',
    },
    heroLayout: {
      'left-text-right-visual': 'Left text / right visual',
      centered: 'Centered',
      'full-bleed': 'Full bleed',
    },
    buttonStyle: {
      rounded: 'Rounded',
      pill: 'Pill',
      square: 'Square',
    },
  },
  messages: {
    loadingSites: 'Loading sites...',
    loadingConfig: 'Loading appearance configuration...',
    configUnavailable: 'No appearance configuration for the selected site.',
    noSites: 'No sites yet. Create a site first.',
    saved: 'Configuration saved',
    invalidPrimaryColor: 'Primary color must be #RRGGBB',
  },
} as const
