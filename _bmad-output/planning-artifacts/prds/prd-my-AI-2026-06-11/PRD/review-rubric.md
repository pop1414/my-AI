# PRD Quality Review — Docling 解析引擎升级 + HybridChunker PRD

## Overall verdict

This is a solid infrastructure-upgrade PRD: it knows what it is, does not pretend to be user-facing, and makes specific, falsifiable claims throughout. The core thesis (unify parsing and chunking onto Docling Serve, remove Tika) is crisply stated and carries every feature decision. What holds it back from "strong" across the board is thinness in strategic grounding — the Vision and Success Metrics sections are absent, and the NFRs are under-specified for an infrastructure change that replaces a runtime bottleneck. An engineer could build from this PRD, but a decision-maker or downstream evaluator would lack the framing to judge whether the build succeeded.

## Decision-readiness — adequate

The PRD is functional for an informed decision-maker but makes them do too much work. The problem statement (SS 1.1) is direct and evidence-backed — "Tika 对复杂格式的解析质量有限" and "维护两条路径的成本" name real costs with approximate LOC counts (260 lines for the Java chunker, 235 for Tika). The "时机" section (SS 1.2) ties to specific ADR entries (D11, D22) and a concrete Arconia version (0.27.1), which is excellent.

However, trade-offs are not surfaced. The PRD documents what is gained (single path, richer metadata, less code) but never names what is lost. Docling Serve is a container-side process with network latency and a failure domain the current in-process Tika path does not have. The PRD acknowledges "Docling Serve 不可用 → 全部解析阻塞" as a risk (SS 9) but does not frame it as a trade-off: are we accepting operational complexity for parsing quality? That choice is implicit, not stated.

### Findings
- **[high]** Unsigned trade-off decision (§ 9 / implicit) — The decision to accept a new container dependency and network hop is never stated as a trade-off. The risk table treats it as a problem to mitigate, not a choice with a forgone alternative (keep Tika in-process). *Fix:* Add a "Decisions" subsection after §1 or within §2 that names: "Decision: Accept container dependency for parsing quality. Forgone: In-process Tika with no network hop."
- **[medium]** Missing Open Questions section — There is no §0.x or dedicated section for open questions. Issues like "How does Docling handle very large PDFs (>100MB)?" or "What happens during Docling rolling restart?" are unasked. *Fix:* Add an Open Questions section with 3–5 items that would surface during implementation.

## Substance over theater — strong

This PRD has almost no theater. Personas are absent by design (the §3 note explicitly states "终端用户无感" and shifts perspective to developers/maintainers — appropriate for an infrastructure upgrade). There is no innovation theater: Docling replacing Tika is a well-known substitution documented in the decision register. NFRs are few and specific (NFR-1, NFR-2, NFR-3), not boilerplate. The Vision statement is absent (noted below under Strategic Coherence), which is itself a form of honesty — this is a capability upgrade, not a new product vision.

### Findings
- **[low]** User story 7 and 8 ("作为系统运维者") are the weakest of the eight — they describe deployment expectations rather than genuine user needs. They are not harmful but add no tension. *Fix:* Merge into FR-1 / FR-13 or delete.

## Strategic coherence — thin

The PRD lacks an explicit thesis or Vision statement. The title ("Docling 解析引擎升级 + HybridChunker") is descriptive, not strategic. A reader can infer the thesis: "Unify on Docling to reduce maintenance cost and improve parsing quality," but this is never stated as a bet the PRD makes. The features (FR-1 through FR-13) do follow from that implicit thesis — there is no scope drift — but without an explicit thesis, it is hard to judge whether the priorities are right.

Success Metrics are absent entirely. For a pure infrastructure upgrade this is not automatically disqualifying, but the PRD would be stronger with one or two of:
- "After migration, mean parse time per PDF does not exceed baseline + 1s"
- "Zero chunk metadata regressions in gold sample suite 90 days post-migration"

Counter-metrics are not named (none exist, since there are no SMs).

The MVP scope kind is clearly "platform" — replacing an internal capability — and the scope logic is coherent. But the PRD does not name this.

### Findings
- **[critical]** No Vision / thesis statement (§ 0 — missing) — The PRD starts directly with "问题陈述." For an infrastructure upgrade that changes the ingest pipeline's failure model and operational dependencies, a one-paragraph strategic framing ("We are betting that ...") is needed. *Fix:* Add a §0 or lead paragraph that states the thesis explicitly.
- **[high]** No Success Metrics (§ 5 — missing) — The NFR section has three operational metrics (latency, storage, startup), but there is no section that defines what success looks like for the whole initiative. An upgrade PRD should answer: "How will we know this was worth it?" at least qualitatively. *Fix:* Add a 3–5 item success metrics table with at least one quantitative criterion.

## Done-ness clarity — adequate

Functional requirements (FR-1 through FR-13, §4) are mostly concrete and verifiable, but many rely on the reader knowing the existing codebase to judge whether the work is complete. For example:

- FR-3 says "新建 DoclingDocumentParser，调用 DoclingServeApi 接收 pre-chunked 结果" — the implied AC is that the parser exists and is wired, but there is no statement of what "done" means: does it handle all eight source formats? Does it fall back? Does it error-handle Docling timeouts?
- FR-12 says "黄金样本重建" — how many samples? What formats? What pass/fail criteria?

The Acceptance Criteria section (§8) helps substantially. Items 1–11 are check-box verifiable. But they are separate from the FRs — an engineer would need to cross-reference.

Non-functional requirements (NFR-1 through NFR-3) are better than average but still underspecified:
- NFR-1: "增加不超过 2s" is a specific delta, but "典型 PDF" is undefined. What page count? What size?
- NFR-2: "约 30% artifact 存储" is a guess — "可观测" means it will be measured, not that 30% is a target.
- NFR-3: "fail-fast" is directional. Does it mean the app doesn't start (container exit), or that the ingest endpoint returns 503? Either is defensible, but the PRD should say.

### Findings
- **[high]** FR-3 missing acceptance conditions (§ 4, FR-3) — "新建 DoclingDocumentParser" says what to build but not what proves it works: format coverage, error modes, timeout handling. *Fix:* Add to §8 or inline: "FR-3 done when: all 8 listed formats produce a valid DocumentParseResult; Docling timeout triggers retry (max 2) then documented fallback."
- **[medium]** NFR-1 uses undefined baseline (§ 5, NFR-1) — "典型 PDF" is not quantified. An engineer cannot test against this. *Fix:* "典型 PDF (10-page text PDF, 2MB) parsed in ≤5s, with within-path variance <20%."
- **[medium]** "可观测" is direction, not a threshold (§ 5, NFR-2) — NFR-2's acceptance is "we'll notice." *Fix:* "Storage savings ≥20% measured over 100-document benchmark suite."

## Scope honesty — strong

The Non-Goals section (§6) is unusually good: nine items, each specific and verifiable. Each one addresses something a reader might reasonably assume is in scope (e.g., "不引入图片理解或视觉问答," "不引入父子分块," "不调整 vector metadata shape"). This is the dimension where the PRD is strongest.

The decision log enforces scope discipline: entries 1–5 are marked Fixed (no ambiguity). Entries 6–7 are Tentative (configuration externalization and observability), which is an honest reflection that those decisions are not yet hardened.

Open-items density is zero (no Open Questions section, no `[ASSUMPTION]` tags, no `[NOTE FOR PM]` callouts). For a 2.5-day implementation this is acceptable — the stakes are low enough that the reader can infer the unknowns. But for an infrastructure change that blocks all upstream document processing, at least one assumption should be explicit.

### Findings
- **[medium]** No `[ASSUMPTION]` tags on explicit inferences (§ 1–2) — The PRD implicitly assumes Docling's output quality exceeds Tika's for all listed formats (PDF, DOCX, PPTX etc.). This is the core bet. It should be tagged. *Fix:* Add `[ASSUMPTION: Docling output quality >= Tika for all listed formats at migration time]` at §2.1.

## Downstream usability — strong

The PRD is easy to source-extract from. FR/UJ IDs are contiguous (FR-1 through FR-13, UJ-1 through UJ-8). The Glossary is missing (no dedicated section), but the domain nouns are consistent enough — "Docling Serve," "DoclingDocumentParser," "ChunkMetadata" are used identically across sections. The user stories correctly use named protagonist roles ("后端开发者," "质量维护者," "系统运维者").

Cross-references resolve: FRs reference FR dependencies correctly; the implementation plan maps back to FRs. The decision log entries map to PRD sections.

The main gap is the missing Glossary. For a project that already has domain models (DocumentChunk, DocumentParseResult, ProcessingMetadataBuilder, etc.), a downstream architect or story writer would benefit from a single source of truth for term definitions.

### Findings
- **[low]** No Glossary section (§ — missing) — Terms like "ChunkMetadata," "ProcessingMetadataBuilder," "ConvertDocumentOptions," "HybridChunkerOptions" are used without definition. *Fix:* Add a Glossary with 6–8 terms relevant to this PRD.

## Shape fit — strong

This is an infrastructure / platform capability upgrade for a brownfield codebase. The PRD's shape matches well:

- User stories with named protagonists that are appropriate for the audience (developers, maintainers, operators) — not UX theater.
- Component change list (§2.2) with specific class names and line counts — correct for brownfield work where downstream implementers need to know what to delete as much as what to build.
- Implementation plan with phase timing (§7) — appropriate for a bounded 2.5-day effort.
- Traceability to decision register entries (D11, D22, D10).

The PRD does not over-formalize. It avoids UJ density that would be overhead for a single-operator tool. The acceptance criteria are operational, not user-experience — correct shape.

### Findings
*(none — strong dimension)*

## Mechanical notes

- **Glossary:** Missing entirely. Six domain terms (DoclingDocumentParser, ChunkMetadata, HybridChunkerOptions, ConvertDocumentOptions, DocumentParseResult, ProcessingMetadataBuilder) are introduced or modified and should be defined in one place.
- **ID continuity:** Clean. FR-1 through FR-13 are contiguous. UJ-1 through UJ-8 are contiguous. No gaps, no duplicates.
- **Assumptions Index roundtrip:** No `[ASSUMPTION]` tags present, so the index is trivially consistent. See Scope Honesty finding about adding at least one.
- **UJ protagonist naming:** Each user story has a named protagonist ("后端开发者," "质量维护者," "系统运维者"). No floating UJs.
- **Required sections present:** Problem Statement, Solution, User Stories, FRs, NFRs, Non-Goals, Implementation Plan, Acceptance Criteria, Risks — all present. Missing: Vision/Thesis, Success Metrics, Open Questions, Glossary.
