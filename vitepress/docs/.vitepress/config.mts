import { defineConfig } from 'vitepress'

export default defineConfig({
  base: '/purelogic/',
  title: "PureLogic",
  description: "Direct-style, pure domain logic for Scala",
  head: [['link', { rel: 'icon', href: '/purelogic/favicon.png' }]],

  themeConfig: {
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Documentation', link: '/getting-started' },
      { text: 'FAQ', link: '/faq' },
    ],

    logo: '/purelogic.svg',

    sidebar: [
      {
        text: 'Documentation',
        items: [
          { text: 'Getting started', link: '/getting-started' },
          { text: 'Why PureLogic?', link: '/why-purelogic' },
          {
            text: 'Capabilities', link: '/capabilities', items: [
              { text: 'Reader', link: '/reader' },
              { text: 'Writer', link: '/writer' },
              { text: 'State', link: '/state' },
              { text: 'Abort', link: '/abort' },
            ]
          }
        ]
      },
      {
        text: 'FAQ', link: '/faq'
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/ghostdogpr/purelogic' }
    ],

    search: {
      provider: 'local'
    }
  }
})
