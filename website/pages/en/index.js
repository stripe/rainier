/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require('react');

const CompLibrary = require('../../core/CompLibrary.js');

const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

class HomeSplash extends React.Component {
  render() {
    const {siteConfig, language = ''} = this.props;
    const {baseUrl, docsUrl} = siteConfig;
    const docsPart = `${docsUrl ? `${docsUrl}/` : ''}`;
    const langPart = `${language ? `${language}/` : ''}`;
    const docUrl = doc => `${baseUrl}${docsPart}${langPart}${doc}`;

    const SplashContainer = props => (
      <div className="homeContainer">
        <div className="homeSplashFade">
          <div className="wrapper homeWrapper">{props.children}</div>
        </div>
      </div>
    );

    const Logo = props => (
      <div className="projectLogo">
        <img src={props.img_src} alt="Project Logo" />
      </div>
    );

    const ProjectTitle = props => (
      <h2 className="projectTitle">
        {props.tagline}
      </h2>
    );

    const PromoSection = props => (
      <div className="section promoSection">
        <div className="promoRow">
          <div className="pluginRowBlock">{props.children}</div>
        </div>
      </div>
    );

    const Button = props => (
      <div className="pluginWrapper buttonWrapper">
        <a className="button" href={props.href} target={props.target}>
          {props.children}
        </a>
      </div>
    );

    return (
      <div className="banner">
      <SplashContainer>
        <div className="inner">
          <ProjectTitle tagline={siteConfig.tagline} title={siteConfig.title} />
          <PromoSection>
            <Button href={docUrl('intro')}>Get Started</Button>
          </PromoSection>
        </div>
      </SplashContainer>
      </div>
    );
  }
}

class Index extends React.Component {
  render() {
    const {config: siteConfig, language = ''} = this.props;
    const {baseUrl} = siteConfig;    

    const TldrSection = ({ language }) => (
      <div className="tldrSection productShowcaseSection lightBackground">
        <Container>
          <div
            style={{
              display: "flex",
              flexFlow: "row wrap",
              justifyContent: "space-evenly"
            }}
          >
            <div style={{ display: "flex", flexDirection: "column" }}>
              <h2>What</h2>
              <ul className="whatSection" style={{ flex: "1" }}>
                <li>Build a probabilistic simulation in idiomatic, immutable Scala</li>
                <li>Match the simulation with real-world observations</li>
                <li>Rainier will infer the parameters for your simulation that could lead to that data</li>
                <li>Make predictions from those parameters to optimize your decision-making</li>
              </ul>
            </div>
            <div style={{ display: "flex", flexDirection: "column" }}>
              <img src="/img/rainier.svg" width="400px"></img>
            </div>
            <div style={{ display: "flex", flexDirection: "column" }}>
              <h2>How</h2>
              <ul className="whySection" style={{ flex: "1" }}>
                <li>Fixed-structure, continuous-parameter models like Stan or PyMC3</li>
                <li>Custom TensorFlow-like computation graph with auto-diff</li>
                <li>Gradient-based inference with Hamiltonian Monte Carlo</li>
                <li>Dynamic compilation to low-level bytecode for speed</li>
                <li>Pure JVM operation for ease of production deployment</li>
              </ul>
            </div>
          </div>
        </Container>
      </div>
    );

    return (
      <div>
        <HomeSplash siteConfig={siteConfig} language={language} />
        <div className="mainContainer">
          <TldrSection/>
        </div>
      </div>
    );
  }
}

module.exports = Index;
