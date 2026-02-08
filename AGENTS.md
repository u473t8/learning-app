<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

## OpenSpec ↔ Beads ↔ GitHub Coordination

### Purpose
Define a repeatable workflow that binds one GitHub issue, one OpenSpec change, and one Beads epic into a single unit of work.

### Hard Invariants
- One GitHub issue ↔ one OpenSpec change ↔ one Beads epic.
- Beads epic references the OpenSpec change only.
- Beads artifacts (`.beads/*`) are committed once, at epic closure, as the last commit of the work.
- If starting new planning on a branch with unstaged or uncommitted changes, stop and resolve: finish the work or at least stage changes before continuing.

### Safety Gate (Before Planning)
1) Check working tree status.
   - If dirty, stop. Finish and commit the current work, or at minimum stage changes and pause planning until clarified.
2) If not on `master`, switch to `master` and pull latest changes.
3) Read `openspec/AGENTS.md` and `openspec/project.md`.
4) Run:
   - `openspec list`
   - `openspec list --specs`

### Seed Selection
1) Find the closest matching GitHub project item by:
   - Keywords and scope
   - Area (UI/App Logic/Infra/Server management)
   - Priority
2) If no suitable item exists:
   - Check for a matching local OpenSpec change.
     - If found: create a new GitHub issue and reference it in that change.
     - If not found: create a new GitHub draft item with proper priority.

### Draft → Issue Conversion
- Convert draft to issue in a single GitHub command.
- Assign to `u473t8`.
- Set status to `In progress`.

### Closed-Issue Conflict
If the closest matching GitHub issue is closed, pause and decide:
- Should the change be closed (no longer needed)?
- Or should it be refined (actually about something else)?
Then proceed by reopening or creating a new issue as appropriate.

### Branch Creation
- Create a branch from the GitHub issue via GitHub (auto-named).
- Checkout locally.

### OpenSpec Planning
- If a matching local change exists, reuse it.
- Otherwise create a new change-id (verb-led, kebab-case).
- Add `## Links` with the GitHub issue URL in `openspec/changes/<id>/proposal.md`.
- Draft planning artifacts per `openspec/AGENTS.md`.
- Validate: `openspec validate <id> --strict --no-interactive`.
- Do not implement before approval.

### Beads Planning
- Create a Beads epic for the OpenSpec change.
- Create Beads tasks from `openspec/changes/<id>/tasks.md` (1:1 with checkboxes).
- Assign all tasks to `u473t8`.
- Mark the first task `in progress`.
- `bd sync` to publish the plan.

### Execution Loop (Per Task)
1) Set Beads task to `in progress`.
2) Implement per tasks/specs.
3) Move task to `review`, assign `u473t8` as reviewer.
4) If reviewer reports findings: move back to `in progress` and fix.
5) If implementation is fine but spec needs refinement: spawn a new OpenSpec change for refinement and continue the current task flow.

### Commit Policy
- After review approval:
  - Stage changes.
  - Propose Conventional Commit message.
  - Commit only after approval.
- Never commit `.beads/*` during task commits.

### Wrap-Up
- After all tasks complete:
  - Run `zprint` on affected `.clj`, `.cljs`, `.edn` files.
  - Create a formatting commit.
- Close Beads epic:
  - `bd sync`
  - Commit Beads artifacts as the next commit.
- Archive OpenSpec change:
  - Sync/merge delta specs as needed.
  - Commit OpenSpec artifacts as the final commit before pushing.
- After archiving: create GitHub draft items (title + summary) for each new change created in the flow.
- Push branch and open PR.
