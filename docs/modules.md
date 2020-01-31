---
id: modules
title: Rainier's Modules
sidebar_label: Modules
---

The diagram below illustrates Rainier's published modules, the dependencies between them, and a representative type for each. It also shows dependencies on external packages.

The modules were designed to be as standalone as possible, so that you can, for example:

* use the sampler implementations without Rainier's compute graph or model API
* build something entirely different on top of the compute graph
* build an alternative modeling API that still makes use of the compute graph and samplers
* use the notebook utilities in a completely different project

![modules.svg](/img/modules.svg)