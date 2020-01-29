---
id: modules
title: Rainier's Modules
sidebar_label: Modules
---

The diagram below illustrates Rainier's published modules, the dependencies between them, and a representative type for each. It also shows dependencies on external packages.

The modules were designed so that, for example, you could use the sampler implementations without Rainier's compute graph or model API; or build something entirely different on top of the compute graph; or build an alternative modeling API that still makes use of the compute graph and samplers.

![modules.svg](/img/modules.svg)