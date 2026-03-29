import { defineConfig } from 'vitepress'

export default defineConfig({
  base: '/purelogic/',
  title: "PureLogic",
  description: "Direct-style, pure domain logic for Scala",
  head: [
    ['link', { rel: 'icon', href: '/purelogic/favicon.png' }],
    ['script', { defer: '', src: 'https://cloud.umami.is/script.js', 'data-website-id': '6a091ffe-f57f-4344-b63d-5c74d19cbfd0' }]
  ],

  themeConfig: {
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Documentation', link: '/getting-started' },
      { text: 'FAQ', link: '/faq' },
      { text: 'About', link: '/about' },
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
      },
      {
        text: 'About', link: '/about'
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
