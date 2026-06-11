# React Web App — Specification

> See `SYSTEM_SPEC.md` for system-wide conventions.

## Responsibility
End-user facing web application for writing and reading journal entries.

## Hosting
**Azure Static Web Apps** (brazilsouth) — auto-deploy from `main` branch via GitHub Actions.

## Technology
| Property | Value |
|---|---|
| Framework | React 19 + TypeScript |
| State (global) | Zustand |
| State (server) | TanStack Query v5 |
| Routing | React Router v7 |
| UI library | shadcn/ui + Tailwind CSS |
| Build tool | Vite |
| API client | Auto-generated from OpenAPI spec |
| Auth | JWT in memory; refresh token in httpOnly cookie |
| Markdown | react-markdown (render entry content) |
| Testing | Vitest + React Testing Library |

## Profile page

The profile page shows account info, preferences, and lifetime stats.

### Account section
- Display name (editable via `PUT /api/users/me`)
- Email (read-only)
- Change password button — opens a modal with three fields:
  current password, new password, confirm new password.
  Calls `PUT /api/users/me/password { currentPassword, newPassword }`.
  On success: shows "Password changed. Please sign in again." and redirects to `/login`
  (backend revokes all refresh tokens, so re-login is required)

### Preferences section (Phase 2)
- **Daily reminder time** — time picker, calls `PUT /api/users/me/preferences { reminderTime }`
- **Weekly summary email** — toggle, calls `PUT /api/users/me/preferences { weeklySummaryEnabled }`

> Phase 2: show these fields in the UI from day one but disable them with a
> "Coming soon" badge until the Notification Service scheduled jobs are implemented.

### Stats section
Fetched from `GET /api/journal/stats`:
- Total entries
- Total words
- Current streak
- Longest streak
- Member since (first entry date or registration date)

### Actions
- Sign out button — calls `DELETE /api/users/logout`, clears Zustand store, redirects to `/login`

## Pages / screens
| Route | Description |
|---|---|
| `/login` | Login form — includes "Forgot password?" link |
| `/register` | Registration form — on success: auto-login and redirect to `/` (Today) |
> After successful registration React calls `POST /api/users/login` automatically
> and redirects to `/`. The user never sees the login page after registering.
| `/forgot-password` | Email input — requests password reset |
| `/reset-password` | New password form — token comes from hash fragment `#token=xxx` (not query param — see Flaw 5) |
| `/` | Today's entry — shows editor pre-loaded with today's date. If entry exists, loads it for editing. |
| `/entries` | List of all past entries, paginated, most recent first |
| `/entries/:date` | View/edit a specific day's entry (format: yyyy-MM-dd) |
| `/profile` | View and edit user profile |

## Entry Detail — read-only view

Clicking a past entry from the sidebar or All Entries list navigates to `/entries/:date`
— a **read-only view** before entering edit mode. Same pattern as Android.

```
/entries/2026-06-11    ← read-only Entry Detail
    ↓ click "Edit entry"
/entries/2026-06-11/edit  ← editor unlocked
```

**Entry Detail page layout:**
- Back arrow (← All entries) in top left
- `Edit entry` button + `Delete` icon in top right
- Date header: `Thursday, June 11 2026`
- Mood emoji + mood label (if set)
- Tags chips row (hidden if no tags)
- Title as `h2` (hidden if null — show first line of content instead)
- Full content rendered as Markdown (`react-markdown`)
- Word count + time at bottom: `142 words · saved at 9:41 AM`

**Navigation:**
- `Edit entry` button → navigates to `/entries/:date/edit` (unlocks editor)
- `Delete` → shows confirmation dialog (same as Today screen delete)
- Back browser button → returns to All entries list

**URL structure:**
- `/entries/2026-06-11` — read-only detail
- `/entries/2026-06-11/edit` — editor mode
- Today's entry always goes directly to `/` (Today screen) — not `/entries/:date`

## Key UX rules
- The home screen (`/`) always opens the editor for **today's date**
- If no entry exists for today, the editor is empty and ready to write
- If an entry already exists for today, it loads automatically for editing
- Past entries are read-only by default, with an "Edit" button to unlock
- The sidebar shows recent entries with mood indicator dot and preview text
- **Null field handling** — all three optional fields must be handled gracefully:
  - `title` is null → show first line of `content` as the entry title in list and sidebar
  - `mood` is null → show no emoji, no mood dot in sidebar
  - `tags` is empty → hide the tags row entirely (no empty space)
- Search box on All entries page calls `GET /api/journal/entries?q=` with 500ms debounce — no search on every keystroke
- Search can be combined with date range filter (calendar selection)
- Empty search results show a "No entries found" state with a clear button
- Days with no entry show a greyed italic "No entry" placeholder in the sidebar to maintain calendar continuity

## UX rules — edge cases and guards

### Delete confirmation (Flaw 1)
- Deleting an entry always shows an `AlertDialog`: "Delete this entry? This cannot be undone."
- After confirmation, show a Toast with "Entry deleted" + **Undo** action for 5 seconds
- The actual `DELETE /api/journal/entries/{id}` call fires only after the 5-second Undo window expires
- If Undo is clicked within 5 seconds → cancel the delete, restore the entry in the UI

### Loading state on initial fetch (Flaw A)
- When the Today screen first loads, fetch today's entry before rendering the editor
- While fetching: show `Skeleton` loader in the editor area (title, content, mood row)
- Never show the blank editor before the fetch resolves — prevents user typing into a stale state
- If fetch returns 404 (no entry today): show empty state
- If fetch returns 200: load entry into editor

### Empty state — daily prompt

When no entry exists for today, show a prompt card below the empty editor area.
Clicking the prompt card focuses the content editor (same as clicking "Start writing").
No text is pre-filled — the prompt is inspiration only.

```typescript
// constants/dailyPrompts.ts — shared with Android (same 15 prompts)
export const DAILY_PROMPTS = [
  "What made you smile today?",
  "What's one thing you're grateful for?",
  "Describe a challenge you faced and how you handled it.",
  "What's something you learned recently?",
  "What do you wish you had more time for?",
  "Describe your ideal day.",
  "What's something you're looking forward to?",
  "What would you tell your past self?",
  "What's something you accomplished today, no matter how small?",
  "What's been on your mind lately?",
  "Who inspired you today and why?",
  "What's one habit you'd like to build?",
  "Describe a moment of peace you experienced recently.",
  "What's something you want to improve about yourself?",
  "What made today different from yesterday?",
] as const

// Rotate by day of year — same prompt all day, changes at midnight
const dayOfYear = Math.floor(
  (Date.now() - new Date(new Date().getFullYear(), 0, 0).getTime()) / 86400000
)
export const todayPrompt = DAILY_PROMPTS[dayOfYear % DAILY_PROMPTS.length]
```

**Prompt card component:**
- Shows only when `entryStatus === "empty"` (404 response)
- Hidden once the user starts typing (entry exists or content field has text)
- Label: `✨ Daily prompt`
- Click → `contentRef.current?.focus()` — focuses content editor
- Same 15 prompts as Android, same day-of-year rotation — consistent cross-platform

### Password strength (Flaw B)
> Password policy is defined in `services/user-service/SPEC.md` — minimum 8 characters, maximum 128. No complexity rules.

**Register page:**
- Show hint below password field: "At least 8 characters"
- Hint turns green (teal) when `password.length >= 8`
- Save button state is driven by `form.formState.isDirty` (react-hook-form):
  - Disabled when: content is empty, or no unsaved changes exist
  - Enabled when: content is not empty AND form has unsaved changes
  - Resets to disabled after autosave fires or manual save succeeds
  - Never blocks typing — only controls the Save button state
- Backend validation still enforced — 400 returned if server rejects

## Client-side field validation

Validation runs on every keystroke. Errors shown inline below the field.
**Never block typing** — show warnings but allow the user to keep writing.

### Registration / Login
| Field | Rule | Error message |
|---|---|---|
| Name | Required, 2–100 chars | "Name must be at least 2 characters" |
| Email | Valid email format | "Enter a valid email address" |
| Password | Min 8, max 128 chars | "Password must be 8–128 characters" |

### Journal entry editor
| Field | Rule | UX behaviour |
|---|---|---|
| Title | Max 255 chars | Character counter shown at 200+ chars: `"220 / 255"` |
| Content | Max 10,000 chars | Live counter always visible bottom-right: `"1,842 / 10,000"` · turns red at 9,500+ |
| Tags | Max 10 per entry | "+ add tag" chip disabled when 10 tags reached · tooltip: "Maximum 10 tags" |
| Tag name | Max 50 chars | Counter shown at 40+ chars: `"47 / 50"` |
| Search | Max 200 chars | Input stops accepting characters at 200 |

### Password change
| Field | Rule | Error message |
|---|---|---|
| Current password | Required | "Enter your current password" |
| New password | Min 8, max 128 chars | "Password must be 8–128 characters" |
| Confirm password | Must match new | "Passwords do not match" |

> **React implementation:** use `react-hook-form` with `zod` schema validation.
> Schemas mirror the backend `@Size`, `@NotBlank`, `@Email` annotations exactly.
- On 400 from backend: show inline error "Password must be at least 8 characters"

**Change password modal:**
- Same hint on the new password field
- Confirm password field: show inline error "Passwords do not match" if they differ before submit

**Reset password page:**
- Same hint on the new password field

### Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+S` / `Ctrl+S` | Save current entry — same as clicking the Save button |

```tsx
// hooks/useEditorShortcuts.ts
useEffect(() => {
  const handler = (e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 's') {
      e.preventDefault()  // prevent browser save dialog
      if (isSaveEnabled) handleSave()
    }
  }
  window.addEventListener('keydown', handler)
  return () => window.removeEventListener('keydown', handler)
}, [isSaveEnabled, handleSave])
```

- Only fires when `isSaveEnabled` is true (content not empty + unsaved changes)
- `e.preventDefault()` prevents the browser's native "Save page" dialog
- Works on both macOS (`Cmd+S`) and Windows/Linux (`Ctrl+S`) via `e.metaKey || e.ctrlKey`
- Applies on Today screen and past entry editor

## Autosave minimum threshold (Flaw 2)
- Save button is disabled until `content.length > 0`
- POST fires only on explicit Save — never automatically
- See Autosave section for full details

### Prev/next day navigation in editor (Flaw 3)
- The editor header shows prev/next day arrows next to the date:
  ```
  ← Jun 5    Saturday, June 6    →
  ```
- "→" (next day) is disabled when viewing today — cannot navigate to the future
- Clicking a date arrow navigates to `/entries/yyyy-MM-dd`
- If no entry exists for that date, shows the empty state with "Write an entry for this day"
- The unsaved changes guard (useBlocker) applies to these arrows too

### Streak reset messaging (Flaw 4)
- When `currentStreak === 0` AND `longestStreak > 0`:
  Show banner: "Your {longestStreak}-day streak ended. Start a new one today!"
- Banner appears only on the Today screen, only on the first open after a missed day
- Dismissed automatically once the user writes today's entry
- Stored in localStorage to avoid showing again after dismissal

### Password reset token in URL (Flaw 5)
- Use hash fragment instead of query param: `/reset-password#token=xxx`
- React reads `window.location.hash` instead of `useSearchParams`
- Hash fragments are not sent to the server, not in Referer headers, not in server logs

### Empty state for zero entries (Flaw 6)
- When `totalEntries === 0` on the All entries page:
  Show full empty state: "No entries yet. Go write your first one!"
  with a button linking to `/` (Today's editor)
- Different from the per-day "No entry" placeholder — this is the whole-list empty state

### Mood change debounce (Flaw 7)
- Mood changes are included in the 30-second autosave debounce
- Selecting a mood does NOT trigger an immediate save
- Mood is sent together with the next debounced PUT call

### Tag removal (Flaw C)
- Each tag chip in the editor shows an × button on hover
- Clicking × removes the tag from the entry immediately in the UI
- Tag removal is included in the 30-second autosave debounce
- Tag removal sets `isDirty = true` → hasUnsavedChanges becomes true

### Sidebar cache invalidation after POST (Flaw D)
- After a successful `POST /api/journal/entries` (first save):
  - Invalidate the TanStack Query `entries` cache key
  - This triggers a refetch of the sidebar recent entries list
  - The new entry appears at the top of the sidebar immediately
- After a successful `PUT /api/journal/entries/{id}` (autosave):
  - Update the entry in the cache directly (no refetch needed)
  - Use TanStack Query `setQueryData` to update the cached entry in place

### Export progress feedback (Flaw E)
- When user clicks Download in the export modal:
  - Disable the Download button immediately (prevent double-click)
  - Show spinner + "Preparing your export..." inside the modal
  - Keep the modal open during the entire download process
  - On success: show "Download ready!" for 1 second then close modal
  - On 404 (no entries): show inline error "No entries found for this date range"
  - On 400 (range too large): show inline error "Date range cannot exceed 366 days"
  - On network error: show "Export failed. Please try again." + re-enable Download button

### Password change redirect (Flaw F)
- After successful `PUT /api/users/me/password`:
  - Do NOT redirect immediately
  - Show success message on the Profile page: "Password updated successfully."
  - Wait 3 seconds, then redirect to `/login`
  - Show a countdown: "Signing you out in 3... 2... 1..."
  - Alternatively: redirect to `/login` with a URL param `?message=password-updated`
    and show "Password updated. Please sign in with your new password." on the login page

### Timezone for entryDate (Flaw G)
- `entryDate` must always use the user's **local timezone**, never UTC
- Implementation:
  ```typescript
  // Always use local date — never new Date().toISOString() (UTC)
  const today = new Date().toLocaleDateString('sv')  // returns yyyy-MM-dd in local time
  ```
- This applies everywhere `entryDate` is set or read:
  - Today screen: `entryDate = today in local time`
  - Prev/next day navigation: increment/decrement local date
  - Calendar component: all dates in local time
  - Streak bar: 7-day window based on local dates
- A user in Brazil writing at 11pm on June 6 sends `entryDate: 2026-06-06` — not June 7

### Dynamic page title (Flaw H)
- The All entries page heading is dynamic based on active filters:
  - Default: "All entries"
  - Date selected from calendar: "June 2026" (month view) or "June 6, 2026" (day selected)
  - Search active: "Results for '{query}'"
  - Both: "Results for '{query}' in June 2026"
- The `<title>` tag (browser tab) should also update:
  - Today screen: "Today · Bravoscribe"
  - All entries: "All entries · Bravoscribe"
  - Entry detail: "{entryDate} · Bravoscribe"
  - Profile: "Profile · Bravoscribe"
  - Use React Router's `useMatches` or a custom `useDocumentTitle` hook

## Streak feature
A 7-day streak bar is displayed at the bottom of the editor. It shows the last 7 calendar days with visual indicators:
- ✅ Green — entry written that day
- 🟣 Purple — today (current day)
- ⬜ Empty — no entry written

**Streak count** — consecutive days with at least one entry, counting backwards from today. Displayed as "N-day streak" with a flame icon. Resets to 0 if a day is missed.

**Implementation:**
- Calculated client-side from the entries list returned by `GET /api/journal/entries`
- No dedicated backend endpoint needed — derive from existing data
- Stored in a Zustand selector, not a separate API call

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+S` / `Ctrl+S` | Save current entry — same as clicking the Save button |

```tsx
// hooks/useEditorShortcuts.ts
useEffect(() => {
  const handler = (e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 's') {
      e.preventDefault()  // prevent browser save dialog
      if (isSaveEnabled) handleSave()
    }
  }
  window.addEventListener('keydown', handler)
  return () => window.removeEventListener('keydown', handler)
}, [isSaveEnabled, handleSave])
```

- Only fires when `isSaveEnabled` is true (content not empty + unsaved changes)
- `e.preventDefault()` prevents the browser's native "Save page" dialog
- Works on both macOS (`Cmd+S`) and Windows/Linux (`Ctrl+S`) via `e.metaKey || e.ctrlKey`
- Applies on Today screen and past entry editor

## Autosave

**Two-phase save model — intent matters:**

| Action | Trigger | API call |
|---|---|---|
| Creating the entry | User clicks Save explicitly | `POST /api/journal/entries` |
| Updating the entry | Autosave after 30s inactivity | `PUT /api/journal/entries/{id}` |

POST is **never** fired automatically. The user must explicitly click Save for the first time. This prevents accidental entry creation from a few keystrokes.

**Autosave behaviour (PUT only, after first save):**
- Fires after **30 seconds** of inactivity (no typing, no mood/tag changes)
- Mood and tag changes are included in the debounce — not saved immediately on change
- Status shown in editor header:
  - Before first save: "not saved yet" — prominent Save button
  - After first save, no changes: "saved"
  - After first save, changes pending: "unsaved changes"
  - Saving in progress: "saving..."
  - Network error: "save failed — retry" with manual Save button
- Manual Save button always visible and functional regardless of autosave state

**`hasUnsavedChanges` definition:**
```typescript
// true when entry exists AND current state differs from last saved version
const hasUnsavedChanges = entryExists && isDirty
// isDirty = content, title, mood, or tags changed since last save
// Before first POST: hasUnsavedChanges is always false (nothing to lose)
```

**Minimum content before Save button is enabled:**
- Content must be non-empty (at least 1 character) to enable the Save button
- Empty entries cannot be created

**Implementation:**
- `useAutosave` custom hook — accepts entry content, title, mood, tags, entryId
- Uses TanStack Query `useMutation` for PUT calls only
- Debounce delay: 30000ms (30 seconds)

## Unsaved changes guard

Prevent accidental data loss when the user navigates away with unsaved changes.

**Two scenarios handled:**

**Scenario 1 — Tab close or browser navigation (beforeunload)**
```typescript
useEffect(() => {
  const handleBeforeUnload = (e: BeforeUnloadEvent) => {
    if (hasUnsavedChanges) {
      e.preventDefault()
      e.returnValue = ''  // required for Chrome
      // Browser shows its own generic dialog — cannot be customised
    }
  }
  window.addEventListener('beforeunload', handleBeforeUnload)
  return () => window.removeEventListener('beforeunload', handleBeforeUnload)
}, [hasUnsavedChanges])
```

**Scenario 2 — In-app navigation (React Router useBlocker)**
When user clicks sidebar nav (All entries, Profile) with unsaved changes:
```typescript
const blocker = useBlocker(
  ({ currentLocation, nextLocation }) =>
    hasUnsavedChanges &&
    currentLocation.pathname !== nextLocation.pathname
)
```
Shows a custom AlertDialog with:
- Title: "Leave without saving?"
- Body: "Your changes will be lost."
- Buttons: "Stay and save" (primary) · "Leave anyway" (destructive)

**When the guard is active:**
- Only when `hasUnsavedChanges === true` (entry exists AND content has changed)
- Never before the first explicit Save — there is nothing to lose yet

## Mood picker
5 mood options displayed as emoji buttons in the editor header:

| Emoji | Value (sent to API) |
|---|---|
| 😄 | `GREAT` |
| 🙂 | `GOOD` |
| 😐 | `NEUTRAL` |
| 😔 | `BAD` |
| 😞 | `TERRIBLE` |

Selected mood is highlighted. Mood is optional — user can write an entry without selecting one.

## Layout
- **Two-column layout:** sidebar (260px fixed) + main editor area (flex 1)
- **Sidebar:** app title, navigation (Today, All entries, Profile), recent entries list
- **Main area:** entry title input, tags row, content textarea, mood picker, save button, streak bar
- **Responsive:** on mobile (< 768px), sidebar collapses into a bottom navigation bar

## Design system

**shadcn/ui + Tailwind CSS** — shadcn/ui components are copied into the project
at `src/components/ui/` and owned by the codebase. Edit them freely.
Built on Radix UI primitives (accessibility) + Tailwind CSS (styling).

### Visual direction
Warm / personal — feels like a real journal, not a productivity app.
Inspired by parchment, ink, leather and aged paper.

### App name
**Bravoscribe**

### Typography
Two-font system — serif for all writing content, sans-serif for UI chrome:

| Use | Font | Weight | Notes |
|---|---|---|---|
| Entry titles | Lora (serif) | 500 | Large, warm, distinctive |
| Entry body text | Lora (serif) | 400 italic | Makes writing feel like a real journal |
| Entry titles in sidebar | Lora (serif) | 500 | Consistent with editor |
| UI chrome (dates, labels, nav) | Inter (sans-serif) | 400 | Clean, readable |
| Buttons, badges | Inter (sans-serif) | 500 | |
| App name wordmark | Lora (serif) | 500 | Letter-spacing: -0.3px |

```css
/* Import in index.html */
@import url('https://fonts.googleapis.com/css2?family=Lora:ital,wght@0,400;0,500;1,400&family=Inter:wght@400;500&display=swap');
```

### Color palette
Warm parchment tones — replaces generic white and Material 3 purple:

```css
/* globals.css */
:root {
  /* Ink — replaces generic grays */
  --ink:           #2C1A0E;   /* primary text — warm near-black */
  --ink-2:         #5C3D1E;   /* body text */
  --ink-3:         #6B4226;   /* secondary text, labels */
  --ink-4:         #C4A882;   /* tertiary text, placeholders */

  /* Parchment — replaces white surfaces */
  --parchment:     #FDFAF3;   /* editor background */
  --parchment-2:   #F5F0E8;   /* sidebar, secondary surfaces */
  --parchment-3:   #EDE5D4;   /* hover states, streak bar */

  /* Accent — primary action color */
  --accent:        #8B4513;   /* buttons, active states, app icon */
  --accent-light:  #F2E4D4;   /* tag backgrounds */

  /* Gold — secondary accent, mood indicators */
  --gold:          #C17D52;   /* good mood, streak flame */
  --gold-light:    #FAEEDA;   /* selected mood background */

  /* Teal — positive states */
  --teal:          #2D6A4F;   /* great mood, saved status */
  --teal-light:    #D8F3DC;   /* saved status background */

  /* Border */
  --border:        #DDD5C0;   /* all borders */

  /* shadcn/ui token overrides */
  --background:    var(--parchment);
  --foreground:    var(--ink);
  --primary:       var(--accent);
  --primary-foreground: #FDFAF3;
  --muted:         var(--parchment-2);
  --muted-foreground: var(--ink-3);
  --border-color:  var(--border);
  --ring:          var(--accent);
}
```

### Mood color system
Mood is shown as a colored left border on entry cards — subtle and elegant:

| Mood | Border color | Meaning |
|---|---|---|
| GREAT | `#2D6A4F` (teal) | Vibrant green |
| GOOD | `#C17D52` (gold) | Warm amber |
| NEUTRAL | `#DDD5C0` (border) | Muted — no emphasis |
| BAD | `#C17D52` (gold-dark) | Warm brown |
| TERRIBLE | `#993C1D` (deep rust) | Dark warm red |

### Key design decisions
- Sidebar is `--parchment-2` (slightly darker than editor) — depth without shadows
- Editor area is `--parchment` (lightest) — the writing space feels open and clean
- No pure white anywhere — everything is warm off-white
- No generic grays — all neutrals are warm brown tones
- Entry body text is `font-style: italic` in Lora — feels like handwriting
- Saved status is teal (positive) not purple — more natural

### Tailwind v4 config

Tailwind v4 is CSS-first — no `tailwind.config.js`. All tokens go in `globals.css`:

```css
/* globals.css */
@import "tailwindcss";

/* Dark mode variant — activates when ancestor has data-theme="chronicle" */
@custom-variant dark (&:where([data-theme="chronicle"], [data-theme="chronicle"] *));

@theme {
  /* Fonts */
  --font-serif: 'Lora', Georgia, serif;
  --font-sans:  'Inter', system-ui, sans-serif;

  /* Warm theme colors (default) */
  --color-ink:           #2C1A0E;
  --color-ink-2:         #5C3D1E;
  --color-ink-3:         #6B4226;
  --color-ink-4:         #C4A882;
  --color-parchment:     #FDFAF3;
  --color-parchment-2:   #F5F0E8;
  --color-parchment-3:   #EDE5D4;
  --color-accent:        #8B4513;
  --color-accent-light:  #F2E4D4;
  --color-gold:          #C17D52;
  --color-gold-light:    #FAEEDA;
  --color-teal:          #2D6A4F;
  --color-teal-light:    #D8F3DC;
  --color-border:        #DDD5C0;

  /* Chronicle theme colors (dark) */
  --color-chronicle-bg:      #080B18;
  --color-chronicle-panel:   #0D1128;
  --color-chronicle-gold:    #C8A84B;
  --color-chronicle-cream:   #E8DFC8;
  --color-chronicle-dim:     #B8AD96;
  --color-chronicle-teal:    #2A8A7A;
  --color-chronicle-ruby:    #A83040;
  --color-chronicle-border:  #3A4A70;
}
```

**Usage:** `dark:` utilities activate automatically when `data-theme="chronicle"` is on any ancestor element. No JS required.

### shadcn/ui component map

| Component | Used for |
|---|---|
| `Button` | Save, Start writing, Export, Sign in, Register |
| `Input` | Login email/password, Register fields, Search box, Title field |
| `Textarea` | Journal content editor |
| `Dialog` | Change password modal, Delete entry confirmation |
| `Sheet` | Export range selector (mobile bottom sheet equivalent) |
| `Card` | Entry cards in All entries list |
| `Badge` | Tag chips on editor and entry cards |
| `Popover` | Tag picker dropdown |
| `Calendar` | Date picker for custom export range |
| `Toast` | Autosave status ("saved", "saving...", "save failed"), errors |
| `Avatar` | Profile initials circle |
| `Separator` | Dividers between editor sections |
| `Select` | Mood picker (alternative to emoji buttons if needed) |
| `AlertDialog` | Confirm before deleting an entry ("Delete this entry? This cannot be undone.") and leaving with unsaved changes ("Leave without saving?") |
| `Skeleton` | Loading placeholders while entries fetch — mandatory on Today screen initial load |
| `ScrollArea` | Scrollable sidebar entry list |
| `DropdownMenu` | Overflow menu on entry cards (edit, delete) |
| `Tabs` | Future use — could separate Today / Recent on home |

### Custom components (not from shadcn/ui)

| Component | Description |
|---|---|
| `MoodPicker` | 5 emoji buttons mapping to GREAT/GOOD/NEUTRAL/BAD/TERRIBLE |
| `StreakBar` | 7-day row of day squares — done/today/empty states |
| `EntryCalendar` | Month grid with purple dots on days with entries |
| `TagInput` | Inline tag chip input with autocomplete from existing tags |
| `AutosaveStatus` | "saving..." / "saved N min ago" / "save failed — retry" |
| `ExportModal` | Range selector + download trigger + loading state |
| `EmptyToday` | Empty state with illustration, prompt, Start writing button |
| `EntryCard` | Entry list item — title/preview/mood/tags/word count |
| `TagChip` | Tag chip with × removal button (hover to reveal) |
| `NoDayCard` | Dashed card for days with no entry + Write now button |

## Export feature

Users can export their journal entries as a zip file containing a single
Markdown file, designed for AI consumption.

**Export ranges:**
| Option | from | to |
|---|---|---|
| Today | today | today |
| This week | Monday of current week | today |
| This month | 1st of current month | today |
| This year | Jan 1 of current year | today |
| All time | date of first entry | today |
| Custom range | user picks from calendar | user picks from calendar |

**UI flow:**
1. User clicks "Export" button (available on All entries page and Today page)
2. A modal opens with range selector (radio buttons + custom date pickers)
3. User clicks "Download" → calls `GET /api/journal/entries/export?from=&to=`
4. Browser receives `application/zip` → triggers file download automatically
5. Modal shows loading state while zip is being generated

**Implementation:**
- Use `fetch` with `response.blob()` + `URL.createObjectURL()` to trigger download
- Show loading spinner during generation
- Show error message if 404 (no entries) or 400 (range too large)

## Auth flow
1. Login → POST `/api/users/login`
2. `accessToken` stored in Zustand (memory only — never localStorage)
   `refreshToken` stored in httpOnly cookie (set by server — React never reads it)
3. Axios interceptor catches 401 → calls `POST /api/users/refresh` (no body — cookie sent automatically)
   → gets new `accessToken` → retries original request
4. Logout calls `DELETE /api/users/logout` (no body — cookie sent automatically) → clears Zustand store

## Password visibility toggle

All password fields on `/login`, `/register`, and `/forgot-password` include a visibility toggle button inside the field (trailing icon).

```tsx
const [visible, setVisible] = useState(false)

<div className="relative">
  <input
    type={visible ? "text" : "password"}
    placeholder="Password"
    className="w-full pr-10 ..."
  />
  <button
    type="button"
    onClick={() => setVisible(v => !v)}
    className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-3"
    aria-label={visible ? "Hide password" : "Show password"}
  >
    {visible ? <EyeOff size={18} /> : <Eye size={18} />}
  </button>
</div>
```

- Default state: password hidden (`type="password"`)
- Icons: `Eye` / `EyeOff` from `lucide-react`
- Applies to: all password fields on Login, Register, and Password Change screens
- `type="button"` — prevents accidental form submission on click
- Accessible: `aria-label` updates with toggle state

## Auth page layout

All three auth pages (`/login`, `/register`, `/forgot-password`) share the same header:

```
┌─────────────────────────────────────┐
│                                  ☀️ │  ← theme toggle
│                                     │
│           📓 Bravoscribe            │  ← h1, Lora serif, text-4xl, ink brown
│   Your daily writing companion      │  ← p, italic, text-sm, ink-3
│                                     │
│   ┌─────────────────────────────┐   │
│   │ Email                       │   │
│   └─────────────────────────────┘   │
│   ┌─────────────────────────────┐   │
│   │ Password                    │   │
│   └─────────────────────────────┘   │
│        Forgot password?             │
│   ┌─────────────────────────────┐   │
│   │          Sign In            │   │  ← full-width rounded button
│   └─────────────────────────────┘   │
│  Don't have an account? Sign up     │
└─────────────────────────────────────┘
```

**Typography:**
- App name: `font-serif text-4xl font-normal tracking-tight text-ink`
- Tagline: `font-serif italic text-sm text-ink-3`
- Spacing between tagline and first field: `mt-12` (48px)

## Session expired handling

When the silent token refresh fails (refresh token expired or revoked):

1. Clear Zustand auth store (`accessToken = null`)
2. Redirect to `/login?reason=session-expired`
3. Login page detects `reason=session-expired` in URL and shows banner:

```tsx
// pages/LoginPage.tsx
const reason = new URLSearchParams(location.search).get('reason')

{reason === 'session-expired' && (
  <div className="bg-amber-50 border border-amber-200 text-amber-800 
                  rounded-lg px-4 py-3 text-sm mb-4">
    ⏱ Your session has expired. Please sign in again.
  </div>
)}
```

- Banner uses Warm theme: amber background, amber border
- Chronicle theme: same amber (neutral warning color, not brand)
- Banner dismissed automatically when user starts typing in either field

## Theme toggle on auth pages

The theme toggle is visible on `/login`, `/register`, and `/forgot-password` — before
the user is authenticated. Theme preference is stored in `localStorage` and applied
immediately with no page reload.

```tsx
// layouts/AuthLayout.tsx
export function AuthLayout({ children }: { children: React.ReactNode }) {
  const { theme, toggle } = useTheme()
  return (
    <div className="min-h-screen bg-parchment dark:bg-chronicle-bg flex flex-col">
      <div className="flex justify-end p-4">
        <button onClick={toggle} aria-label="Toggle theme">
          {theme === 'warm' ? <Sun size={20} /> : <Moon size={20} />}
        </button>
      </div>
      <div className="flex-1 flex items-center justify-center">
        {children}
      </div>
    </div>
  )
}
```

- Toggle sits in the top-right corner of every auth page
- Uses the same `useTheme` hook as the main app — no separate state
- Theme persists across login — if user set Chronicle before logging in,
  they see Chronicle immediately after login with no flash

## Forgot password flow
1. User clicks "Forgot password?" on `/login`
2. Redirected to `/forgot-password` — enters email
   Page shows a "← Back to login" link at the top for users who clicked by accident
3. Calls `POST /api/users/password-reset/request { email }`
4. Always shows "If an account exists for that email, a reset link has been sent." — never reveals whether email exists
5. User clicks link in email → lands on `/reset-password#token=xxx`
6. Enters new password + confirm password
7. Calls `PUT /api/users/password-reset/confirm { token, newPassword }`
8. On success → shows "Password changed. Please sign in." → redirects to `/login`
9. On 400 (expired or invalid token) → shows "This reset link is invalid or has expired. Request a new one." with link back to `/forgot-password`

## Security headers

Azure Static Web Apps supports security headers via `staticwebapp.config.json`:

```json
{
  "globalHeaders": {
    "Content-Security-Policy": "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' https://[apim-name].azure-api.net; frame-ancestors 'none'",
    "X-Frame-Options": "DENY",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "strict-origin-when-cross-origin",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()"
  }
}
```

> Replace `[apim-name]` with the actual APIM hostname per environment.
> The `unsafe-inline` on `style-src` is required for Tailwind CSS runtime styles.

## Environment variables
```env
VITE_API_BASE_URL=https://[apim-name].azure-api.net
```

## AI Regeneration Prompt
```
Using SYSTEM_SPEC.md and frontend-react/SPEC.md, generate the full React
project scaffold for the Bravoscribe app. Use shadcn/ui + Tailwind CSS
as the design system. Follow the component map in the Design system section
— use shadcn/ui components for all listed UI elements, and generate all
custom components listed (MoodPicker, StreakBar, EntryCalendar, TagInput,
AutosaveStatus, ExportModal, EmptyToday, EntryCard, NoDayCard). Include: Vite + TypeScript,
React Router routes for all pages listed, Zustand auth store, TanStack
Query with axios JWT interceptor, login and register pages, protected route
wrapper, two-column layout with sidebar and main editor, today's journal
editor page (loads existing entry or blank), entries list page with calendar
component (dots from /entries/dates endpoint), individual entry page, mood
picker component (5 emoji buttons mapping to GREAT/GOOD/NEUTRAL/BAD/
TERRIBLE), useAutosave custom hook (debounced 30s, PUT or POST, shows
saving/saved/error status), streak bar component (7-day window, calculated
from entries list, no extra API call), tag input component, export modal
(6 range options, fetch blob + createObjectURL for zip download), and
profile page (account info, stats from GET /api/journal/stats, phase 2
preferences shown as disabled with Coming soon badge, change password modal
that calls PUT /api/users/me/password and redirects to login on success),
search on All entries page (GET /api/journal/entries?q= with 500ms debounce,
combinable with date range filter, empty state with clear button),
prev/next day navigation arrows in editor header (disabled on today/future),
streak reset banner when currentStreak=0 and longestStreak>0,
delete confirmation AlertDialog with 5-second Undo toast before actual API call,
unsaved changes guard using window.beforeunload and React Router useBlocker
(custom AlertDialog: "Leave without saving?" / "Stay and save" / "Leave anyway"),
password reset token read from hash fragment not query param,
zero-entries empty state on All entries page,
autosave debounce 30 seconds PUT only (POST requires explicit Save button click),
mood changes included in 30s debounce not saved immediately,
tag chips with × removal button (hover to reveal, triggers isDirty),
Skeleton loader on Today screen while fetching today's entry (never show blank editor before fetch resolves),
TanStack Query cache invalidation after POST (refetch sidebar entries list),
setQueryData after PUT (update entry in cache without refetch),
export modal keeps open during download with spinner and progress messages,
password change shows 3-second countdown before redirect to login,
entryDate always in local timezone using toLocaleDateString('sv') never UTC,
dynamic page heading on All entries (default/month/day/search),
document title updates per route using useDocumentTitle hook,
tag chip removal with x button on hover.
```
