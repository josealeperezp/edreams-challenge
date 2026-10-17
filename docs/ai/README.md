# AI tools used

- **Claude Code** (CLI) running the Claude Fable 5.1 model, used as a pair
  programmer from the terminal.
- **CLAUDE.md** at the repository root is the standing instruction set the agent
  loads every session: architecture rules, conventions, commands and the
  definition of done. It is the "agent system" engineered for this codebase.
- **prompts.md** is the chronological log of the prompts that produced
  non-trivial work, with what was accepted, changed or rejected.

Working method: every AI-generated change is reviewed and run locally. The
test suite and the ArchUnit rules gate what is kept.
