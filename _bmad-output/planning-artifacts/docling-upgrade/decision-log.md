# Decision Log — Docling PRD

## 2026-06-11

| # | Decision | Status | Notes |
|---|----------|--------|-------|
| 1 | Scope: D11(Docling) + D22(HybridChunker) + D10(docker-compose) as single PRD | Fixed | |
| 2 | No dual-track — Tika removed immediately after switch | Fixed | |
| 3 | Markdown/HTML/TXT also go through Docling, no retained Java chunker | Fixed | |
| 4 | Old gold samples replaced with new ones post-migration | Fixed | |
| 5 | Storage interface unchanged, only DocumentParseResult field shape changes | Fixed | |
| 6 | Chunking parameters externalized to application.yaml | Confirmed | P1 scope, ~15 LOC |
| 7 | Basic observability metrics (parse duration, error count) in scope | Confirmed | P1 scope, Micrometer counters |
| 8 | Trade-off: accept container dependency (Docling Serve) for parsing quality upgrade | Fixed | Forgone: in-process Tika zero-hop. Added §0. |
| 9 | Success Metrics: 4 quantitative SM (code reduction, metadata richness, latency, success rate) | Fixed | Added §5. |
| 10 | FR-3 completion conditions: 8 formats, Docling 4xx→FAILED, 5xx/timeout→retry | Fixed | Added FR-3a. |
| 11 | Cascade change affected files: 15+ files documented in §11.1 | Fixed | Mitigation: global search at phase-3 start and end. |
| 12 | TextCleaningService: retain `cleanNativeMarkdown` for initial pass, decide later | Fixed | §11.2. |
| 13 | TXT ChunkMetadata: all three fields nullable/fillable (empty list, 0, PARAGRAPH) | Fixed | §11.3. |

## Review Findings Triage (2026-06-11)

| Finding | Severity | Resolution |
|---------|----------|------------|
| No Vision / thesis statement | Critical → resolved | Added §0 战略定位. |
| Unsigned trade-off decision | High → resolved | §0 names forgone alternative explicitly. |
| Missing Success Metrics | High → resolved | Added §5 成功指标 (SM-1..SM-4). |
| FR-3 missing completion conditions | High → resolved | Added FR-3a. |
| Compilation breakage risk undocumented | High → resolved | Added §11.1 级联改动面 table. |
| No Open Questions section | Medium | Added §12 待确定事项. |
| NFR-1 uses undefined baseline | Medium → resolved | Tightened to "10页文本PDF" in SM-3. |
| No [ASSUMPTION] tags | Medium → resolved | Added one assumption block at §1.2. |
| Weak UJ-7 and UJ-8 | Low → deferred | Appropriate for infra PRD; merge into FR not needed. |
| No Glossary | Low → deferred | Terms used consistently; add if downstream architects request. |
| Docling timeout/4xx/5xx error mapping missing | Medium → resolved | Added FR-3a + AC-13. |
| TXT ChunkMetadata empty-field behavior | Medium → resolved | Added §11.3. |
| REJECT route HTTP status consistency | Medium → resolved | AC-7 tightened to 415. |
| Docling startup model download timeout | High → resolved | Added risk row + AC-1 tightened. |
| TextCleaningService fate | Medium → resolved | §11.2 documents phased approach. |

## Finalization

PRD status set to `final` at 2026-06-11. 5 blockers resolved, 1 low deferred (Glossary). Next: `bmad-create-architecture` (ADR), then `bmad-create-epics-and-stories`.
