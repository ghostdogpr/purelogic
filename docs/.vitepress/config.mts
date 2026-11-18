import { defineConfig } from 'vitepress'

export default defineConfig({
  base: '/purelogic/',
  title: "PureLogic",
  description: "Direct-style, pure domain logic for Scala",
  head: [
    ['link', { rel: 'icon', href: '/purelogic/favicon.png' }],
    ['script', { defer: '', src: 'https://cloud.umami.is/script.js', 'data-website-id': '2ba71608-2c44-4547-804e-ed78f3a41ad1' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:url', content: 'https://ghostdogpr.github.io/purelogic/' }],
    ['meta', { property: 'og:title', content: 'PureLogic' }],
    ['meta', { property: 'og:description', content: 'Direct-style, pure domain logic for Scala' }],
    ['meta', { property: 'og:image', content: 'https://ghostdogpr.github.io/purelogic/og.png' }],
    ['meta', { property: 'og:image:width', content: '1200' }],
    ['meta', { property: 'og:image:height', content: '630' }],
    ['meta', { property: 'og:image:alt', content: 'PureLogic logo (Scala bars over a lightning bolt) above the title PureLogic: Direct-style, pure domain logic for Scala' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }]
  ],

  themeConfig: {
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Documentation', link: '/getting-started' },
      { text: 'FAQ', link: '/faq' },
      { text: 'About', link: '/about' },
      { text: 'Scaladoc', link: 'https://javadoc.io/doc/com.github.ghostdogpr/purelogic_3/' },
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
              { text: 'Event Sourcing', link: '/event-sourcing' },
            ]
          },
          { text: 'Capture Checking', link: '/capture-checking' }
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
