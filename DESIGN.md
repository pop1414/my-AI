---
version: 1.0
name: Unified-RAG-Design-System
description: A unified design system merging the technical precision of Supabase (for admin/console surfaces) with the editorial, AI-first warmth of Cursor (for QA and reading surfaces). The system uses a pure white/grey-ladder canvas with strict 1px hairline borders, no shadows, and an Emerald primary CTA. Code surfaces use JetBrains Mono. The QA flow incorporates pastel AI-state colors to indicate reasoning stages.

colors:
  primary: "#3ecf8e"
  primary-deep: "#24b47e"
  ink: "#171717"
  ink-secondary: "#212121"
  ink-mute: "#707070"
  ink-faint: "#b2b2b2"
  hairline: "#e6e5e0"
  hairline-strong: "#cfcdc4"
  canvas: "#ffffff"
  canvas-soft: "#fafafa"
  canvas-night: "#1c1c1c"
  surface-card: "#ffffff"
  on-primary: "#171717"
  on-dark: "#ffffff"
  timeline-thinking: "#dfa88f"
  timeline-grep: "#9fc9a2"
  timeline-read: "#9fbbe0"
  timeline-edit: "#c0a8dd"
  semantic-error: "#cf2d56"
  semantic-success: "#1f8a65"

typography:
  display-lg:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 36px
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: -0.72px
  display-md:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 28px
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: -0.42px
  title-md:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 18px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0
  body-lg:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: 0
  body-md:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  body-sm:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  caption-uppercase:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 11px
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: 0.88px
    textTransform: uppercase
  code:
    fontFamily: "'JetBrains Mono', 'Fira Code', monospace"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  button:
    fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.0
    letterSpacing: 0

rounded:
  none: 0px
  xs: 4px
  sm: 6px
  md: 8px
  lg: 12px
  xl: 16px
  pill: 9999px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  base: 16px
  md: 20px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 80px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 8px 16px
  button-primary-active:
    backgroundColor: "{colors.primary-deep}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.sm}"
  button-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    border: "1px solid {colors.hairline-strong}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 8px 16px
  card-base:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: 32px
    border: "1px solid {colors.hairline}"
  code-pane-dark:
    backgroundColor: "{colors.canvas-night}"
    textColor: "{colors.on-dark}"
    typography: "{typography.code}"
    rounded: "{rounded.md}"
    padding: 16px
  timeline-pill-thinking:
    backgroundColor: "{colors.timeline-thinking}"
    textColor: "{colors.ink}"
    typography: "{typography.caption-uppercase}"
    rounded: "{rounded.pill}"
    padding: 4px 10px
  timeline-pill-grep:
    backgroundColor: "{colors.timeline-grep}"
    textColor: "{colors.ink}"
    typography: "{typography.caption-uppercase}"
    rounded: "{rounded.pill}"
    padding: 4px 10px
  timeline-pill-read:
    backgroundColor: "{colors.timeline-read}"
    textColor: "{colors.ink}"
    typography: "{typography.caption-uppercase}"
    rounded: "{rounded.pill}"
    padding: 4px 10px
  timeline-pill-edit:
    backgroundColor: "{colors.timeline-edit}"
    textColor: "{colors.ink}"
    typography: "{typography.caption-uppercase}"
    rounded: "{rounded.pill}"
    padding: 4px 10px
---

## Overview

This unified design language is engineered for a Knowledge-driven RAG System that serves two distinct modes: the Administrator (Console/Management) and the User (QA/Reading). The base canvas is **pure white** (`{colors.canvas}` — #ffffff) and off-white (`{colors.canvas-soft}` — #fafafa) with near-black ink (`{colors.ink}` — #171717) carrying the typography. The single brand voltage is **Emerald Green** (`{colors.primary}` — #3ecf8e) reserved for primary CTAs. 

Typography runs **Inter** (or a similar clean sans) at weight 500 for display with tight negative letter-spacing, and **JetBrains Mono** on every code, log, or data surface. Depth is achieved strictly through **hairline borders** (`{colors.hairline}` — #e6e5e0); there are no drop shadows in this system.

A signature element borrowed from Cursor is the **AI-timeline pill palette** (peach, mint, blue, lavender) used exclusively in the QA flow to mark AI reasoning stages (Thinking / Searching / Reading / Generating).

**Key Characteristics:**
- Pure white canvas with greyscale hierarchy. Ink is near-black (#171717).
- Single CTA color: `{colors.primary}` (Emerald Green #3ecf8e). Used scarcely, with near-black text on top.
- Display weight stays at 500 with negative letter-spacing for an engineered look.
- JetBrains Mono carries all code, logs, and document chunk references.
- AI timeline pastels: Dedicated tokens for in-product agent action stages (only in QA).
- Compact 6px CTA radius — developer dialect.
- Hairline-only depth; absolutely no drop shadows.

## Colors

### Brand & Accent
- **Emerald** (`{colors.primary}` — #3ecf8e): Primary CTA pills, dot indicators.
- **Emerald Deep** (`{colors.primary-deep}` — #24b47e): Press state.

### Surface
- **Canvas** (`{colors.canvas}` — #ffffff): Pure white page floor.
- **Canvas Soft** (`{colors.canvas-soft}` — #fafafa): Slightly tinted background for chat streams or secondary zones.
- **Surface Card** (`{colors.surface-card}` — #ffffff): Pure white card surface.
- **Canvas Night** (`{colors.canvas-night}` — #1c1c1c): Deep near-black used in code blocks and data panes.

### Hairlines
- **Hairline** (`{colors.hairline}` — #e6e5e0): 1px divider for panels and cards.
- **Hairline Strong** (`{colors.hairline-strong}` — #cfcdc4): Stronger panel outline for focus states.

### Text
- **Ink** (`{colors.ink}` — #171717): Display, body emphasis. Near-black.
- **Ink Secondary** (`{colors.ink-secondary}` — #212121): Cooler near-black for body.
- **Ink Mute** (`{colors.ink-mute}` — #707070): Secondary text.
- **On Primary** (`{colors.on-primary}` — #171717): Near-black text on the Emerald primary fill (NOT white).
- **On Dark** (`{colors.on-dark}` — #ffffff): Text on canvas-night surfaces.

### Timeline (AI-action signature)
- **Thinking** (`{colors.timeline-thinking}` — #dfa88f): Peach.
- **Grep** (`{colors.timeline-grep}` — #9fc9a2): Mint. Searching Vector DB.
- **Read** (`{colors.timeline-read}` — #9fbbe0): Pastel blue. Reading Docs.
- **Edit** (`{colors.timeline-edit}` — #c0a8dd): Lavender. Generating Answer.

### Semantic
- **Success** (`{colors.semantic-success}` — #1f8a65): System success indicators.
- **Error** (`{colors.semantic-error}` — #cf2d56): Validation errors.

## Typography

### Font Family
**Inter** (or similar geometric humanist sans like Circular) is the primary UI family. Fallback: `'Helvetica Neue', Helvetica, Arial, sans-serif`. Code surfaces strictly use **JetBrains Mono**.

### Hierarchy

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| `{typography.display-lg}` | 36px | 500 | 1.2 | -0.72px | Section heads / Console titles |
| `{typography.display-md}` | 28px | 500 | 1.2 | -0.42px | Sub-section heads |
| `{typography.title-md}` | 18px | 500 | 1.4 | 0 | Component/Card titles |
| `{typography.body-lg}` | 18px | 400 | 1.55 | 0 | Lead text |
| `{typography.body-md}` | 16px | 400 | 1.5 | 0 | Default body |
| `{typography.body-sm}` | 14px | 400 | 1.5 | 0 | Helper text / Small data |
| `{typography.caption-uppercase}` | 11px | 600 | 1.4 | 0.88px | Section labels, timeline pill labels |
| `{typography.code}` | 13px | 400 | 1.5 | 0 | Code blocks — JetBrains Mono |
| `{typography.button}` | 14px | 500 | 1.0 | 0 | CTA pill labels |

### Principles
- **Weight 500 across display.** Mid-weight reads as engineered.
- **Negative tracking on display.** Tightens the letterforms into editorial density.
- **JetBrains Mono on every code/data surface.**

## Layout

### Spacing System
- **Base unit:** 8px.
- **Tokens:** `{spacing.xxs}` 4px · `{spacing.xs}` 8px · `{spacing.sm}` 12px · `{spacing.base}` 16px · `{spacing.md}` 20px · `{spacing.lg}` 24px · `{spacing.xl}` 32px · `{spacing.xxl}` 48px · `{spacing.section}` 80px.

### Grid & Container
- **Admin Console:** Full width or dense sidebar navigation layout. Padding `{spacing.xxl}`.
- **QA / Chat:** Centered max-width (~1000px-1200px) with generous whitespace (`{spacing.section}`).

### Whitespace Philosophy
The Admin side uses a tight, dense grid suitable for data tables. The QA side switches to a generous editorial pacing with plenty of breathing room, utilizing `{colors.canvas-soft}` to frame the chat stream.

## Elevation & Depth

The system uses **hairline-only depth**. No drop shadows, no elevation tiers. Cards float above the canvas via 1px hairlines.

| Level | Treatment | Use |
|---|---|---|
| Flat | `{colors.canvas}` or `{colors.canvas-soft}` | Base backgrounds |
| Card | 1px `{colors.hairline}` border | Content cards, Data tables |
| Dark Pane | `{colors.canvas-night}` (#1c1c1c) | Inside code/log mockups |

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.none}` | 0px | Edge bleeds |
| `{rounded.xs}` | 4px | Form inputs |
| `{rounded.sm}` | 6px | Buttons (Signature square-ish radius) |
| `{rounded.md}` | 8px | Compact nested cards / Alerts |
| `{rounded.lg}` | 12px | Primary Cards, Data panels |
| `{rounded.xl}` | 16px | Modals |
| `{rounded.pill}` | 9999px | Timeline pills, badges |

## Components

### Buttons

**`button-primary`** — Background `{colors.primary}`, text `{colors.on-primary}` (near-black, NOT white), type `{typography.button}`, padding 8px × 16px, rounded `{rounded.sm}` (6px).

**`button-primary-active`** — Press state. Background `{colors.primary-deep}`.

**`button-secondary`** — Background `{colors.canvas}`, text `{colors.ink}`, 1px `{colors.hairline-strong}` border, padding 8px × 16px, rounded `{rounded.sm}`.

### Cards

**`card-base`** — Background `{colors.surface-card}`, text `{colors.ink}`, type `{typography.body-md}`, rounded `{rounded.lg}`, padding 32px, 1px `{colors.hairline}` border.

**`code-pane-dark`** — Background `{colors.canvas-night}`, text `{colors.on-dark}` in `{typography.code}` (JetBrains Mono 13px), rounded `{rounded.md}` (8px), padding 16px.

### AI Timeline (Signature)

**`timeline-pill-thinking`** — Peach pill. Background `{colors.timeline-thinking}`, text `{colors.ink}`, type `{typography.caption-uppercase}`, rounded `{rounded.pill}`, padding 4px × 10px. 

**`timeline-pill-grep`** — Mint pill. Same shape, background `{colors.timeline-grep}`.

**`timeline-pill-read`** — Pastel-blue pill. Background `{colors.timeline-read}`.

**`timeline-pill-edit`** — Lavender pill. Background `{colors.timeline-edit}`.

## Do's and Don'ts

### Do
- Reserve `{colors.primary}` (Emerald) for primary CTAs.
- Render display tiers at weight 500 with negative letter-spacing.
- Use `{rounded.sm}` 6px for buttons — square-ish radii, never pill-shaped.
- Use near-black `{colors.ink}` on the emerald button (not white) — the green reads as "lit".
- Render every code/data surface in JetBrains Mono.
- Use timeline pastels only inside in-product agent visualizations (QA flow) — never as system action colors.

### Don't
- Don't bump display weight above 500.
- Don't use pill-shaped buttons for CTAs; the brand's button radius is square-ish 6px.
- Don't use white text on the emerald button.
- Don't add drop shadows. Hairlines + canvas contrast carry the depth.
- Don't use timeline pastels on Admin UI. They're scoped to the AI timeline only.

## Responsive Behavior

### Breakpoints

| Name | Width | Key Changes |
|---|---|---|
| Mobile | < 768px | Display tiers scale down; Admin panels switch to stacked rows. |
| Tablet | 768–1024px | Feature grids 2-up. |
| Desktop | > 1024px | Full Admin dense grid; QA flow capped at 1200px max-width. |

### Touch Targets
- Buttons hit ≥ 36×36px on mobile; vertical padding scales up.
- Form fields stay at 36px minimum height.

## Iteration Guide

1. Focus on a single component at a time.
2. CTAs default to `{rounded.sm}` (6px). Cards use `{rounded.lg}` (12px).
3. Use `{token.refs}` everywhere — never inline hex.
4. Keep emerald scarce; one filled green button per viewport.
5. The white-canvas / no-shadow commitment is non-negotiable.