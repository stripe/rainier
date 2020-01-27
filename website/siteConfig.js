/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// See https://docusaurus.io/docs/site-config for all the possible
// site configuration options.

const siteConfig = {
  title: 'Rainier', // Title for your website.
  tagline: 'Bayesian inference for Scala',
  url: 'https://samplerainier.com', // Your website URL
  baseUrl: '/', // Base URL for your project */
  customDocsPath: "rainier-docs/target/mdoc",

  // Used for publishing and more
  projectName: 'rainier',
  organizationName: 'stripe',

  // For no header links in the top nav bar -> headerLinks: [],
  headerLinks: [
    {doc: 'intro', label: 'Docs'},
    {href: 'https://github.com/stripe/rainier', label: "GitHub", external: true }
  ],

  /* path to images for header/footer */
  headerIcon: 'img/rainier.svg',
  footerIcon: 'img/rainier.svg',

  /* Colors for website */
  colors: {
    primaryColor: 'rgb(17, 42, 68)',
    secondaryColor: '#1EC2EF',
    heroBackgroundColor: 'rgba(14, 40, 68, 0.94)',
    tintColor: '#005068',
    backgroundColor: '#f5fcff',
  },

  /* Custom fonts for website */
  /*
  fonts: {
    myFont: [
      "Times New Roman",
      "Serif"
    ],
    myOtherFont: [
      "-apple-system",
      "system-ui"
    ]
  },
  */

  highlight: {
    // Highlight.js theme to use for syntax highlighting in code blocks.
    theme: 'mono-blue',
  },

  // Add custom scripts here that would be placed in <script> tags.
  scripts: [
    'https://buttons.github.io/buttons.js',
  ],
  // On page navigation for the current documentation page.
  onPageNav: 'separate',
  // No .html extensions for paths.
  cleanUrl: true,

  // Open Graph and Twitter card images.
  ogImage: 'img/undraw_online.svg',
  twitterImage: 'img/undraw_tweetstorm.svg',

  // For sites with a sizable amount of content, set collapsible to true.
  // Expand/collapse the links and subcategories under categories.
  // docsSideNavCollapsible: true,

  // Show documentation's last contributor's name.
  // enableUpdateBy: true,

  // Show documentation's last update time.
  // enableUpdateTime: true,

  // You may provide arbitrary config keys to be used as needed by your
  // template. For example, if you need your repo's URL...
  //   repoUrl: 'https://github.com/facebook/test-site',
};

module.exports = siteConfig;
