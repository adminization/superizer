# ui-kit

`cx.m42.superizer:ui-kit`, package `cx.m42.superizer.kit`: Superizer's component kit. These are
[shadcn/ui](https://ui.shadcn.com)-style components for Compose Multiplatform, built on
`compose.foundation` alone, with a light and a dark theme and [Lucide](https://lucide.dev) icons.
The kit depends on nothing else from Superizer, and `ui` exports it.

- **How to build a screen with it, where it comes from, and how to add a component:**
  [docs/12-ui-kit.md](../docs/12-ui-kit.md)
- **Every component and parameter:** [docs/06-ui-api.md §0](../docs/06-ui-api.md#0-the-kit-cxm42superizerkit)
- **Old `ui.components` widget → kit component:** [docs/06-ui-api.md → Moving to ui-kit](../docs/06-ui-api.md#moving-to-ui-kit)

The design comes from shadcn/ui. The Compose code was adapted from
[RikkaUI](https://github.com/rainxchzed/RikkaUi) (Apache-2.0) and
[shadcn-multiplatform](https://github.com/jershell/shadcn-multiplatform) (MIT). Neither library is a
dependency (see the guide, §1).
