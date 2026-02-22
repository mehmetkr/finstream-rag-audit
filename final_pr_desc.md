## Final Polish

**Goal:** Remove a redundant null check identified in the last code review.

### Change
- The `if (transaction.description() != null)` block was removed from `RuleGateService`.
- **Rationale:** The `Transaction` record's compact constructor now enforces that the description can never be null, making this check unreachable dead code. This change aligns the service layer with the domain model's invariants.

### Verification
- `./gradlew check` passes.
- Code is now cleaner and correctly reflects domain constraints.
