# KasirKita POS Android Design System

## 1. Purpose and Authority

This document is the authoritative Android/Compose visual and interaction guide for KasirKita.

It is milestone-independent. It describes stable design rules, not a list of currently shipped screens.

Feature contracts and exact fields belong to feature or API specifications. Milestone status, implementation progress, and test counts belong in their respective project documents.

## 2. Product Context

KasirKita supports OWNER, ADMIN, and CASHIER roles in fast retail operations. The product must help people complete transactions quickly, prevent mistakes, and continue working through expected connectivity problems.

The design serves non-technical users on phones and tablets. It must make the next safe action obvious without hiding important state or requiring users to learn gestures.

## 3. Design Character

### Calm Operational Retail

KasirKita should feel:

- Calm: quiet surfaces, clear hierarchy, and restrained motion.
- Reliable: visible state, predictable navigation, and honest feedback.
- Efficient: fast scanning, short paths, and stable monetary values.
- Information-dense but breathable: useful detail without crowding or decoration.
- Native Material 3: use the platform language and existing Compose tokens consistently.
- Operational rather than decorative: every visual element should help recognition, choice, or recovery.

Majoo and other POS products may inform workflow and information hierarchy, but KasirKita must not be visually cloned.

## 4. Core Principles

- Function over decoration.
- POS workflow first.
- One clear next action.
- Real state and real data only.
- Progressive disclosure: show what is needed now and reveal detail when it helps a decision.
- Consistency before novelty.
- Accessibility is required, not a finishing step.
- Offline state must remain understandable.
- Optimize transaction speed and mistake prevention.
- Keep operation simple for non-technical users.
- Make primary, secondary, and destructive actions visually distinct.
- Use a minimum 48dp touch target.
- Never expose technical error messages to users.
- Do not hide critical actions behind gestures or unexplained affordances.

Avoid vanity dashboards, fabricated metrics, decorative charts, and collections of cards that do not support an operational decision. Operational dashboards are allowed when they use real, actionable data. Home may become an operational dashboard showing shift, sales, payment, held-order, and synchronization state.

## 5. Visual Foundations

### Color

- Preserve KasirKita primary green `#006C4C`.
- Use primary green for the primary action, selected navigation, active shift state, focused controls, and important totals when emphasis is useful.
- Use `primaryContainer` sparingly for selected or active context, not as a default card fill.
- Do not make every surface green.
- Preserve semantic warning, error, success, and offline colors. Meaning must not depend on brand color alone.
- Support both light and dark themes through Material color roles.
- Prefer theme roles such as `primary`, `onPrimary`, `surface`, `onSurface`, `outline`, and semantic containers over one-off colors.

### Typography

Use a stable hierarchy for:

- Screen identity.
- Operational state.
- Primary amount or action.
- Section title.
- Supporting metadata.

Avoid marketing-sized headings and excessive bold. Keep monetary values stable, aligned, and easy to compare. Use the existing Material typography scale and shared type tokens before adding a new text style.

### Spacing

- Use an 8dp base rhythm.
- Use 4dp only for tightly related content such as a label and its value.
- Keep layouts compact but touch-safe.
- Avoid both cramped content and excessive whitespace.
- Reuse shared spacing tokens before introducing screen-specific values.

### Shape and elevation

- Use standard surfaces around a 12dp radius.
- Use a larger radius only for a primary focal surface where it improves grouping.
- Keep elevation minimal.
- Prefer borders and tonal surfaces over heavy shadows.
- Avoid excessive pills. Reserve them for compact status or filter states that genuinely benefit from the shape.

### Touch targets

- Interactive controls must have a minimum 48dp touch target.
- Icons require correct semantics and a visible label when the meaning is not universally clear.
- Critical actions must not depend on hidden gestures.

## 6. Adaptive Application Structure

### Phone

- Use role-aware app-wide bottom navigation.
- Use no more than five persistent destinations.
- Show both a meaningful icon and a visible label.
- Keep the selected destination clear through state, color, and semantics.
- Hide persistent navigation during focused workflows when it would compete with the task.

### Tablet

- Use the equivalent app-wide navigation rail for the same role-aware destinations.
- Preserve the wide POS catalog plus embedded cart workspace.
- Do not display a second POS-only rail inside the POS screen.
- Do not merely stretch a phone layout across a tablet. Reflow content into the established tablet workspace.
- Use the established 840dp boundary where applicable, including the wide POS and other explicitly adaptive editors.

### Focused flows

Persistent navigation should normally be hidden during:

- Authentication.
- Outlet and shift gates.
- Cart and checkout.
- Receipt viewing or printing.
- Edit, create, and stock forms.
- Focused printer and receipt-template settings tasks.

Focused flows must provide clear back behavior and must preserve entered data across recoverable errors.

## 7. Screen Patterns

### Home

Home is the operational starting point, not a settings menu. It should provide:

- Compact identity, outlet, and shift context.
- One primary operational action, normally entry to the POS or the next shift action.
- Only real and actionable metrics.
- Clear synchronization, offline, held-order, or recovery state when relevant.
- Role-appropriate content without inaccessible teasers.

Administration belongs in `Lainnya`, not in a long list of Home actions.

### Lainnya

`Lainnya` is the role-aware home for secondary operations and administration.

- Group related entries into short list sections.
- Give each navigable entry a semantic icon and clear title.
- Add short supporting text only when it improves a decision.
- Use a chevron only for navigation.
- Separate logout visually from ordinary settings.
- Never show inaccessible actions, disabled feature teasers, or dead destinations.

### Product catalog

- Remain text-first until real product media exists.
- Make product name and price quick to scan.
- Treat stock and category as supporting information.
- Do not fabricate product photos or thumbnails.
- Deterministic category cues, such as a meaningful initial or category color, may be used when they reflect real product data.
- Preserve a wide catalog plus embedded cart on tablet.

### Forms

- Use reusable form principles rather than documenting a fixed set of fields here.
- Give every field a clear label and useful validation.
- Make required and optional state understandable.
- Preserve entered values after recoverable errors.
- Keep the primary save or continue action clear.
- Require confirmation for destructive actions when recovery is not immediate.
- Exact fields and validation contracts belong to the feature or API specification because they may change independently.

### Lists and details

- Prefer aligned rows and meaningful grouping.
- Avoid wrapping every row in a decorative card.
- Keep repeated metadata visually consistent.
- Make the most important value or state easy to locate without scanning every label.

### Dialogs

- Use dialogs only for focused confirmation or small decisions.
- Do not place long forms or complex workflows inside dialogs.
- Make cancellation and the safe action explicit.

## 8. Component Behavior

Use existing Compose tokens and shared components before adding alternatives.

- Filled primary actions: the single most important action in the current context.
- Outlined secondary actions: useful alternatives that should remain visible without competing with the primary action.
- Text or tertiary actions: low-emphasis navigation or reversible supporting actions.
- Destructive actions: clear destructive color, explicit wording, and confirmation when appropriate.
- Navigation items: role-aware, labeled, selected-state aware, and consistent between phone and tablet.
- Cards and surfaces: grouping for useful state or content, not a card per action.
- Status indicators: semantic, concise, and understandable without color alone.
- Empty-state actions: explain what is empty and offer the next safe action.
- Monetary values: stable formatting, clear currency, and visual priority appropriate to the task.

## 9. Application States

Every data-driven screen must define behavior for:

- Loading: show progress or skeleton treatment without pretending data is complete.
- Successful zero or empty data: distinguish a valid zero from a failed request.
- Offline with cached context: show what is cached and label its freshness.
- Partial data: explain which portion is available and keep available actions usable.
- Recoverable error: explain the problem in user language and offer retry or another safe action.
- Blocking error: explain why the task cannot continue and what the user can do next.
- Retry: allow a clear retry without duplicating submissions or requests.
- Stale or last-updated data: show freshness when it could affect a decision.
- Saving or submitting: prevent accidental duplicate actions and communicate progress.
- Duplicate-action prevention: disable or guard repeat submission while the original operation is pending.

Do not represent unavailable remote data as zero. Do not make offline or stale data look current.

## 10. Content and Error Language

- Indonesian interface terminology must be concise and consistent.
- Never expose HTTP codes, stack traces, SQL errors, exception names, or internal identifiers.
- Explain what happened and the next safe action.
- Prefer specific messages such as `SKU sudah digunakan` over technical descriptions.
- Avoid generic marketing copy such as `Welcome back`.
- Avoid unnecessary punctuation and overly conversational UI copy.
- Use action labels that describe the result, such as `Simpan`, `Bayar`, `Coba lagi`, or `Tutup`.

## 11. Accessibility

Require validation for:

- TalkBack descriptions for meaningful controls and icons.
- Selected navigation semantics.
- Logical focus order.
- Font scaling without clipped or overlapping content.
- Sufficient text, icon, and state contrast.
- Minimum 48dp touch targets.
- Keyboard and IME behavior, including resize and focus retention.
- State communication that does not rely on color alone.
- Visible labels for primary navigation.

## 12. Motion

- Keep motion minimal and functional.
- Use standard Material state and navigation transitions.
- Respect reduced-motion expectations.
- Do not add ornamental bouncing, shimmer, glow, or animated decoration.

## 13. Anti-Slop Rules

Do not introduce:

- Gradients.
- Glassmorphism.
- Glow.
- Decorative background patterns.
- Excessive shadows.
- Oversized hero sections.
- Generic marketing headings.
- Arbitrary statistics.
- Fake charts.
- Fake product photos.
- Emoji as production icons.
- Letter placeholders as icons.
- Unnecessary badges and pills.
- Card-per-action layouts.
- Grids of identical administrative cards.
- Disabled feature teasers.
- Duplicate navigation shells.
- Visual elements without operational purpose.

## 14. Visual QA Requirements

Validate every meaningful UI change on:

- Phone portrait.
- Phone landscape where relevant.
- Tablet portrait and landscape.
- The 840dp boundary.
- Light and dark themes.
- System insets and safe areas.
- Font scaling.
- Long Indonesian text.
- Loading, empty, offline, error, and success states.
- OWNER, ADMIN, and CASHIER variations.
- Screenshots before final UI sign-off.

## 15. Governance

- `DESIGN.md` contains stable design rules.
- Milestone status and test counts belong in `PROJECT_STATUS.md`.
- Route lists and API behavior belong in technical specifications.
- Any new reusable visual pattern must update `DESIGN.md`.
- Feature-specific exceptions must be justified in the feature specification or review record.
- Do not introduce a parallel design system.
- Customer and staff management may consume this system when implemented; their feature contracts do not belong here.
