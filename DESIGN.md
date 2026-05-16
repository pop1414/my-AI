---
version: 1.0
name: Unified-RAG-Design-System
description: A unified design system merging the technical precision of Supabase (for admin/console surfaces) with the editorial, AI-first warmth of Cursor (for QA and reading surfaces). The system uses a pure white/grey-ladder canvas with strict 1px hairline borders, no shadows, and an Emerald primary CTA. Code surfaces use JetBrains Mono. The QA flow incorporates pastel AI-state colors to indicate reasoning stages.

colors:
  # Primary Brand (from Supabase)
  primary: "#3ecf8e"
  primary-deep: "#24b47e"
  on-primary: "#171717" # Dark text on green button

  # Canvas & Surfaces (Supabase purity)
  canvas: "#ffffff"
  canvas-soft: "#fafafa"
  canvas-night: "#1c1c1c" # For code blocks / dark panes
  on-dark: "#ffffff"

  # Ink & Text (Hybrid: Supabase contrast, Cursor warmth)
  ink: "#171717"
  ink-secondary: "#212121"
  ink-mute: "#707070"
  ink-faint: "#b2b2b2"

  # Borders / Depth (Hairline only, no shadows)
  hairline: "#e6e5e0"      # Base border
  hairline-strong: "#cfcdc4" # Focused / active border

  # AI State Timeline (from Cursor - ONLY for QA/Chat reasoning)
  ai-thinking: "#dfa88f" # Peach
  ai-reading: "#9fbbe0"  # Blue
  ai-searching: "#9fc9a2" # Mint (Grep)
  ai-generating: "#c0a8dd" # Lavender

  # Semantic
  semantic-error: "#cf2d56"
  semantic-success: "#1f8a65"

typography:
  # Base Fonts
  font-sans: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"
  font-code: "'JetBrains Mono', 'Fira Code', monospace" # Cursor signature

  # Display (Negative tracking, mid-weight)
  display-lg:
    fontSize: 36px
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: -0.72px
  display-md:
    fontSize: 28px
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: -0.42px

  # Body (Clean, readable)
  body-lg:
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.55
  body-md:
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
  
  # UI & Code
  button:
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.0
  code:
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
  badge:
    fontSize: 11px
    fontWeight: 600
    letterSpacing: 0.88px
    textTransform: uppercase

rounded:
  xs: 4px
  sm: 6px   # Signature button radius
  md: 8px
  lg: 12px  # Standard card radius
  full: 9999px # Pills/Badges

spacing:
  xs: 4px
  sm: 8px
  md: 12px
  base: 16px
  lg: 24px
  xl: 32px
  section-dense: 48px # Admin panels
  section-loose: 80px # QA/Reading flow

components:
  # --- Core UI ---
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 8px 16px
  
  button-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    border: "1px solid {colors.hairline-strong}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 8px 16px

  card-base:
    backgroundColor: "{colors.canvas}"
    border: "1px solid {colors.hairline}"
    rounded: "{rounded.lg}"
    shadow: "none" # Explicitly NO drop shadows

  # --- Admin / Console Surfaces (Supabase Vibe) ---
  admin-panel:
    padding: 32px
    layout: "Dense grid, strict hairlines"
  
  code-pane-dark:
    backgroundColor: "{colors.canvas-night}"
    textColor: "{colors.on-dark}"
    typography: "{typography.code}"
    rounded: "{rounded.md}"
    padding: 16px

  # --- QA / User Surfaces (Cursor Vibe) ---
  chat-canvas:
    backgroundColor: "{colors.canvas-soft}"
    padding: "{spacing.section-loose}"
    layout: "Centered max-width (1200px), generous whitespace"
  
  ai-state-pill:
    backgroundColor: "{colors.ai-thinking}" # Varies by state
    textColor: "{colors.ink}"
    typography: "{typography.badge}"
    rounded: "{rounded.full}"
    padding: 4px 10px
---

# Unified RAG Design System

## 1. Design Philosophy

This design system unifies the needs of a **Knowledge-driven RAG System**. It must serve two distinct user modes without breaking brand coherence:
1. **The Administrator (Console/Management):** Needs data density, clear boundaries, and technical control. We draw heavily from **Supabase** here—pure white backgrounds, crisp 1px hairlines, and high-contrast dark panes for logs or raw JSON.
2. **The User (QA/Reading):** Needs focus, editorial calm, and clear feedback on AI processes. We draw from **Cursor** here—generous whitespace, IDE-like reading panes, JetBrains Mono for code, and distinct pastel colors indicating the AI's reasoning states.

**The Glue:** The entire system shares the same DNA:
- **No drop shadows.** Depth is created purely through 1px hairlines (`#e6e5e0`) and canvas layer contrast (`#ffffff` vs `#fafafa`).
- **Emerald Primary CTA.** (`#3ecf8e`). Used sparingly for the most important actions.
- **Square-ish Radii.** 6px for buttons, 12px for cards. Never heavily rounded.

## 2. Color Architecture

- **Canvas & Chrome:** We use a pure white (`#ffffff`) canvas for primary content and off-white (`#fafafa`) for backgrounds or secondary zones. Lines are drawn with fine grey (`#e6e5e0`).
- **Typography Ink:** Near-black (`#171717`) for high contrast readability.
- **The Brand Voltage:** Emerald Green (`#3ecf8e`). Note: Text on the primary green button is *dark* (`#171717`), not white. This makes the button look like a "lit indicator" rather than a painted shape.
- **The AI State Palette:** The only time we break the black/white/green rule is during the RAG pipeline execution in the user chat interface. We use Cursor's pastel timeline pills to explain what the AI is doing:
  - 🍑 Peach (`#dfa88f`): Thinking / Planning
  - 🌿 Mint (`#9fc9a2`): Searching Vector DB / Grepping
  - 💧 Blue (`#9fbbe0`): Reading Documents
  - 🔮 Lavender (`#c0a8dd`): Generating Answer

## 3. Typography

- **UI & Reading:** `Inter` (or similar clean sans-serif). Display fonts use slight negative tracking (`-0.42px` to `-0.72px`) for a tight, engineered look. Weight rarely exceeds 500.
- **Data & Code:** `JetBrains Mono`. This is mandatory for *all* code blocks, JSON payloads in the admin panel, and reference citations in the QA flow. It instantly signals "developer tool".

## 4. Module Implementation Guidelines

### A. The Admin Console (Supabase Mode)
- **Layout:** Full width or dense sidebar navigation.
- **Visuals:** Heavy use of `card-base` (white card, hairline border). Data tables should have no alternating row colors, just 1px bottom borders.
- **Interactions:** Configuration forms, permission toggles, and document upload zones should feel technical and precise.

### B. The QA & Reading Interface (Cursor Mode)
- **Layout:** A constrained center column (max 1000px - 1200px) with massive horizontal margins.
- **Visuals:** The chat stream should sit on an off-white canvas (`#fafafa`). User queries are simple text. AI responses are preceded by the **AI State Timeline** pills (e.g., [🔍 SEARCHING DOCS] -> [💧 READING] -> Answer).
- **Code/References:** When the AI cites a document or writes code, it appears in a `code-pane-dark` or a clearly delineated hairline card with `JetBrains Mono` font, mimicking an IDE pane.

## 5. Strict Rules (Do's and Don'ts)

- **DO** use JetBrains Mono for anything related to raw data, code, or document chunk metadata.
- **DO** keep the Emerald CTA scarce. If there are multiple buttons, use the `button-secondary` (outline) style.
- **DON'T** use drop shadows. Ever. The design must look flat and engineered.
- **DON'T** use the pastel AI colors (Mint, Peach, etc.) for system alerts (like "Upload Success"). They are exclusively for the AI's internal monologue timeline. System success is semantic green (`#1f8a65`).
