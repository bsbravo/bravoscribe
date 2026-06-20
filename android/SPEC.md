# Android App — Specification

> See `SYSTEM_SPEC.md` for system-wide conventions.

## Responsibility
Native Android mobile client for end users to write and read journal entries on the go.

## Technology
| Property | Value |
|---|---|
| Language | Kotlin |
| UI framework | Jetpack Compose |
| Design system | Material 3 — custom warm theme |
| App name | **Bravoscribe** |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Networking | Retrofit 2 + OkHttp + Kotlin Coroutines |
| Local storage | Room (offline cache of entries) |
| Navigation | Compose Navigation |
| Auth storage | EncryptedSharedPreferences |
| Markdown | Compose Markdown library |
| Testing | JUnit 5 + Turbine + MockK |

## Visual direction
Warm / personal — matches the React web app. Parchment tones, serif typography for writing content, ink brown palette instead of Material 3 defaults.

## Material 3 theme — two themes

### LightWarmColorScheme (default)

```kotlin
// ui/theme/Color.kt — Warm palette
val Ink          = Color(0xFF2C1A0E)
val Ink2         = Color(0xFF5C3D1E)
val Ink3         = Color(0xFF6B4226)
val Ink4         = Color(0xFFC4A882)
val Parchment    = Color(0xFFFDFAF3)
val Parchment2   = Color(0xFFF5F0E8)
val Parchment3   = Color(0xFFEDE5D4)
val Accent       = Color(0xFF8B4513)   // primary — saddle brown
val AccentLight  = Color(0xFFF2E4D4)
val Gold         = Color(0xFFC17D52)
val GoldLight    = Color(0xFFFAEEDA)
val Teal         = Color(0xFF2D6A4F)
val TealLight    = Color(0xFFD8F3DC)
val Border       = Color(0xFFDDD5C0)

// ui/theme/Theme.kt — light color scheme
private val LightColorScheme = lightColorScheme(
    primary          = Accent,
    onPrimary        = Parchment,
    primaryContainer = AccentLight,
    onPrimaryContainer = Ink,
    background       = Parchment,
    onBackground     = Ink,
    surface          = Parchment,
    onSurface        = Ink,
    surfaceVariant   = Parchment2,
    onSurfaceVariant = Ink3,
    outline          = Border,
    outlineVariant   = Parchment3,
)

private val LightWarmColorScheme = LightColorScheme
```

### DarkChronicleColorScheme — FFT-inspired midnight blue + gold

```kotlin
// ui/theme/Color.kt — Chronicle palette
val ChronicleDeep     = Color(0xFF080B18)
val ChroniclePanel    = Color(0xFF0D1128)
val ChronicleGold     = Color(0xFFC8A84B)
val ChronicleCream    = Color(0xFFE8DFC8)
val ChronicleDim      = Color(0xFFB8AD96)
val ChronicleTeal     = Color(0xFF2A8A7A)
val ChronicleRuby     = Color(0xFFA83040)
val ChronicleBorder   = Color(0xFF3A4A70)

val DarkChronicleColorScheme = darkColorScheme(
    primary          = ChronicleGold,
    onPrimary        = ChronicleDeep,
    background       = ChronicleDeep,
    onBackground     = ChronicleCream,
    surface          = ChroniclePanel,
    onSurface        = ChronicleCream,
    onSurfaceVariant = ChronicleDim,
    outline          = ChronicleBorder,
    error            = ChronicleRuby,
    onError          = Color.White,
)
```

### BravoscribeTheme composable

```kotlin
// ui/theme/Theme.kt
@Composable
fun BravoscribeTheme(isDark: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (isDark) DarkChronicleColorScheme else LightWarmColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = BravoscribeTypography,
        content     = content
    )
}
```

### Theme preference storage

```kotlin
val Context.themeDataStore by preferencesDataStore("theme_prefs")
val IS_DARK_KEY = booleanPreferencesKey("is_dark")
```

## Typography — Material 3 type scale

```kotlin
// ui/theme/Type.kt
val BravoscribeTypography = Typography(
    // Entry titles — Lora serif
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize   = 20.sp,
        lineHeight = 28.sp,
        color      = Ink,
    ),
    // Entry body text — Lora italic
    bodyLarge = TextStyle(
        fontFamily  = FontFamily.Serif,
        fontWeight  = FontWeight.Normal,
        fontStyle   = FontStyle.Italic,
        fontSize    = 14.sp,
        lineHeight  = 24.sp,
        color       = Ink2,
    ),
    // UI chrome — default sans-serif
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 11.sp,
        color      = Ink3,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize   = 10.sp,
        color      = Ink4,
    ),
)
```

> Font files are in `assets/fonts/` — see `assets/fonts/README.md` for download instructions.
> Copy `lora_variable.ttf` and `lora_variable_italic.ttf` to `android/app/src/main/res/font/`.

## Mood color system (matches React)
| Mood | Color | Usage |
|---|---|---|
| GREAT | `Teal (#2D6A4F)` | Left border on entry cards |
| GOOD | `Gold (#C17D52)` | Left border on entry cards |
| NEUTRAL | `Border (#DDD5C0)` | Left border on entry cards |
| BAD | `Gold dark (#C17D52)` | Left border on entry cards |
| TERRIBLE | `#993C1D` (deep rust) | Left border on entry cards |

## Architecture layers
```
UI (Composables)
    ↓
ViewModel (StateFlow · UI state)
    ↓
UseCase
    ↓
Repository (interface)
    ↓
RemoteDataSource (Retrofit)  +  LocalDataSource (Room)
```

---

## Screens and navigation

### Screen inventory
| Screen | Route | Description |
|---|---|---|
| Login | `login` | Email + password + "Forgot password?" link + theme toggle: `Icons.Default.LightMode` / `Icons.Default.DarkMode` (top-right) |
| Register | `register` | Name, email, password + "Already have an account? Sign in" link → Login + theme toggle: `Icons.Default.LightMode` / `Icons.Default.DarkMode` (top-right) |

### Password visibility toggle

All password fields on Login, Register, and Password Change screens include a visibility toggle icon inside the field (trailing icon).

```kotlin
var passwordVisible by remember { mutableStateOf(false) }

OutlinedTextField(
    value = password,
    onValueChange = { password = it },
    label = { Text("Password") },
    visualTransformation = if (passwordVisible)
        VisualTransformation.None
    else
        PasswordVisualTransformation(),
    trailingIcon = {
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
                imageVector = if (passwordVisible)
                    Icons.Default.VisibilityOff
                else
                    Icons.Default.Visibility,
                contentDescription = if (passwordVisible)
                    "Hide password"
                else
                    "Show password"
            )
        }
    }
)
```

- Default state: password hidden (`PasswordVisualTransformation`)
- Toggle icon: `Icons.Default.Visibility` (eye open) / `Icons.Default.VisibilityOff` (eye closed)
- Applies to: Login, Register, Password Change (all 3 password fields)
- Accessibility: `contentDescription` changes with state

### Auth screen layout

Both Login and Register screens share the same header structure:

```
┌─────────────────────────────────┐
│                             ☀️  │  ← theme toggle (top-right)
│                                 │
│                                 │
│          Bravoscribe            │  ← app name, Lora serif, 34sp, onBackground
│   Your daily writing companion  │  ← tagline, italic, 14sp, onSurfaceVariant
│                                 │
│                                 │  ← 52dp spacing
│  ┌─────────────────────────┐   │
│  │ Email                   │   │  ← OutlinedTextField
│  └─────────────────────────┘   │
│  ┌─────────────────────────┐   │
│  │ Password           👁   │   │  ← password visibility toggle
│  └─────────────────────────┘   │
│                  Forgot password│  ← right-aligned, primary color
│                                 │
│  ┌─────────────────────────┐   │
│  │         Sign In         │   │  ← FilledButton, full width, rounded
│  └─────────────────────────┘   │
│        ─────── or ───────       │
│  Don't have an account? Sign up │  ← center-aligned
└─────────────────────────────────┘
```

**Typography rules:**
- App name: `BravoscribeTypography.displayMedium` — Lora serif, 34sp, `onBackground` color
- Tagline: `BravoscribeTypography.bodyMedium` — italic, 14sp, `onSurfaceVariant` color
- Button label: `BravoscribeTypography.labelLarge` — Inter, 16sp, bold

**Warm theme:** parchment background, ink brown button, saddle brown accents
**Chronicle theme:** midnight blue background, gold button, cream text

| Forgot password | `forgot-password` | Email input → request reset |
| Reset password | `reset-password/{token}` | New password form |
| Home (Today) | `home` | Today's entry editor — empty state or loaded entry |
| Editor (past) | `editor/{entryId}` | Edit a specific past entry |
| Entry List | `entries` | All entries with calendar + search |
| Entry Detail | `entries/{entryId}` | Read-only view of a past entry |
| Profile | `profile` | Account info, stats, preferences |

### Bottom navigation bar
3 tabs — visible on all authenticated screens except Editor:
```
Today (`Icons.Default.Edit`)  |  Entries (`Icons.Default.CalendarMonth`)  |  Profile (`Icons.Default.Person`)
```
Active tab highlighted with Material 3 nav indicator pill.

### Navigation flow
```
Login ──→ "Forgot password?" ──→ Forgot Password ──→ (email sent)
  ↓ success                            ↓ email link
Home (Today)                      Reset Password ──→ Login
  ↓ bottom nav
Entry List ──→ Entry Detail ──→ Editor (past entry)
                                    ↓ back
                              Entry Detail
                                    ↓ back
                              Entry List

Home → tap "Start writing" → Editor opens inline (same screen)

Register ──→ auto-login ──→ Home (Today)
```

### Back stack behaviour
- Entry Detail → Edit entry → Editor (past)
- Back from Editor (past) → Entry Detail (not Entry List)
- Back from Entry Detail → Entry List
- Back from Editor (new entry, unsaved) → show "Leave without saving?" dialog
  - "Stay and save" → remain in editor
  - "Leave" → discard and return to Home

### Theme toggle on Login and Register screens

The theme toggle icon is visible before authentication — top-right corner of both
Login and Register screens.

```kotlin
// The toggle reads/writes DataStore directly — no auth required
IconButton(onClick = { viewModel.toggleTheme() }) {
    Icon(
        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
        contentDescription = "Toggle theme"
    )
}
```

Theme applied at app startup from DataStore — correct theme renders immediately on
the auth screen, before any login. Chronicle users never see a flash of Warm.

### Register → Home transition
After successful registration, the app **auto-logs in** and navigates directly to Home.
The user never sees the Login screen after registering — same behaviour as React web.

---

## Screen designs

### Home screen — Today's entry

**State 1: Empty (no entry yet today)**
- `Icons.Default.Book` (notebook) + "No entry yet today" message
- Subtitle: "What's on your mind? Take a moment to write your thoughts for today."
- "Start writing" filled button (primary color)
- Tapping the **daily prompt card** triggers the same action as tapping "Start writing"
  — opens the editor inline. No text is pre-filled. The prompt served as inspiration only.
- Daily writing prompt at bottom — rotates by day of year (index = dayOfYear % prompts.size)
- Prompt is **not fetched from backend** — hardcoded list in the app:

```kotlin
val DAILY_PROMPTS = listOf(
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
)

val todayPrompt = DAILY_PROMPTS[LocalDate.now().dayOfYear % DAILY_PROMPTS.size]
```
- Streak bar shown with today's slot as **dashed purple square** (not filled yet)
- Streak message: "N-day streak — write today to keep it!" when streak > 0

**State 2: Editor open (blank — after tapping Start writing)**
- Top bar: "Bravoscribe" + "not saved yet" status
- Date subtitle: "Saturday, June 6 2026 · today"
- Mood picker (5 emoji buttons) — none selected
- Title field placeholder: "Title (optional)"
- Content field placeholder: "What's on your mind today?" with blinking cursor
- Tags row: "+ add tag" chip only
- **Save button** — full width, primary color, bottom of screen above bottom nav
  - Enabled only when content is not empty
  - Triggers immediate `POST /api/journal/entries` on first save
  - After first save: button label changes to "Save changes" and triggers `PUT`
  - Autosave still active at 30 seconds — Save button is a manual override
- Streak bar with today's slot still dashed (not saved yet)
- Status: "not saved yet"

**State 3: Entry saved**
- Status pill: "saved" (purple background)
- Today's streak slot turns **solid purple**
- Streak count increments: "🎉 5-day streak!"
- Content visible and editable
- Autosave active — saves 30 seconds after last keystroke

### Home screen — top bar
| Scenario | Top bar content |
|---|---|
| Today, no entry | "Bravoscribe" + overflow menu |
| Today, entry exists | "Bravoscribe" + "saved" pill + overflow menu |
| Today, unsaved changes | "Bravoscribe" + "not saved yet" + overflow menu |

### Editor screen — prev/next day navigation

The editor top bar shows ← / → arrows to navigate between days without returning to the Entry List:

```
← Jun 11  |  Jun 12 (today)  |  → (disabled — no future)
```

- `←` navigates to the previous day — fetches that entry (or shows empty if no entry)
- `→` navigates to the next day — disabled when viewing today (cannot navigate to future)
- Arrows use `Icons.Default.ChevronLeft` and `Icons.Default.ChevronRight`
- If no entry exists for the navigated date: show the empty editor (State 1 equivalent)
- Navigation updates the URL/back stack: back button returns to previous date viewed

### Editor screen — past entry

Reached from: Entry Detail → "Edit entry" button.

**Differences from Today's editor:**
- `Icons.Default.ArrowBack` in top bar (navigates back to Entry Detail)
- **Top bar title** = entry date ("June 3, 2026") not "Bravoscribe"
- **Purple banner** below top bar: "Editing a past entry · N days ago"
- **No streak bar** — replaced by explicit "Save changes" button at bottom
- **Snackbar** on save: "Editing Jun 3 · changes saved" with UNDO action
- Status: "saved" pill in top bar after first save

### Entry List screen

**Sort order:** newest first (most recent entry at top)

**Loading state:** show shimmer skeleton cards while entries load — 3 placeholder cards with animated grey shimmer. Never show a blank screen.

**Search:**
- Search field expands when `Icons.Default.Search` is tapped in top bar
- Real-time search with 500ms debounce — same as React
- Queries `GET /api/journal/entries?q={term}`
- Search results highlight matching text in entry title/content
- Empty search result state: magnifying glass icon + "No entries found for '{term}'"
- Clear search: `×` button dismisses field and restores full list

**Pull-to-refresh:**
- Standard Android pull-to-refresh gesture on Entry List
- Triggers `GET /api/journal/entries` fresh from server
- Updates Room cache with new data

### Entry Detail screen
- Back arrow in top bar
- `Icons.Default.Edit` and `Icons.Default.Delete` in top bar
- Mood emoji + mood label + date
- Tags chips row (hidden if no tags)
- Title (hidden if null — show first line of content instead)
- Full content (Markdown rendered)
- Word count + created time at bottom
- "Edit entry" filled button → navigates to `editor/{entryId}`

### Profile screen
- Avatar initials circle (first letter of name)
- Name + email
- **2×2 stats grid:** total entries · total words · current streak · longest streak
- Account section: Name (editable) · Change password
- Notifications section: Daily reminder toggle (Coming soon — disabled) 
- Sign out button (red/error color)

---

## Optional fields — null handling

`title`, `mood` and `tags` are all optional. Handle null gracefully everywhere:

| Field | If null | Display |
|---|---|---|
| `title` | null | Show first line of `content` as title in list and detail |
| `mood` | null | No emoji shown anywhere |
| `tags` | empty | Hide tags row entirely — no empty space |

---

## Streak bar behaviour

The streak bar shows the last 7 calendar days:

| Day state | Visual |
|---|---|
| Has entry | Solid purple square |
| Today — entry saved | Solid purple square (darkest) |
| Today — no entry yet | Dashed purple square |
| No entry, past day | Grey square |

Streak count label:
- No active streak: "Start your streak today!"
- Active streak, today not written: "N-day streak — write today to keep it!"
- Active streak, today written: "🎉 N-day streak!"

Data source: `GET /api/journal/entries/dates?from={6daysAgo}&to={today}`

---

## Export feature

Users can export journal entries as a zip file containing a Markdown file
designed for AI consumption.

**Export ranges:** Today · This week · This month · This year · All time · Custom

**UI flow:**
1. Export icon in top bar on Home and Entry List screens
2. Bottom sheet slides up with range radio options
3. User taps "Download" → calls `GET /api/journal/entries/export?from=&to=`
4. Response (`application/zip`) saved to Downloads folder via `DownloadManager`
5. System notification: "Journal export downloaded"
6. User can share to any app via Android share sheet (Claude, ChatGPT, Files)

**Implementation:**
- Retrofit `@Streaming` + `ResponseBody` to stream zip
- `Environment.DIRECTORY_DOWNLOADS` via `DownloadManager`
- `LinearProgressIndicator` inside bottom sheet during download
- Snackbar error for 404 (no entries) and 400 (range > 366 days)

---

## Timezone for entryDate
- `entryDate` must always use the user's **local timezone**, never UTC
- Implementation in Kotlin:
  ```kotlin
  // Always local date — never Instant.now() converted to UTC date
  val today = LocalDate.now()  // uses device timezone automatically
  ```
- `LocalDate.now()` in Kotlin/Java uses the device's default timezone
- This applies to: Home screen today date, prev/next navigation, calendar, streak bar

## Password policy
> Defined in `services/user-service/SPEC.md` — minimum 8 characters, maximum 128. No complexity rules.

## Client-side field validation

Use `OutlinedTextField` with `isError` and `supportingText` for inline errors.
Never block typing — show warnings but allow the user to keep writing.

### Registration / Login

| Field | Rule | Error message |
|---|---|---|
| Name | Required, 2–100 chars | "Name must be at least 2 characters" |
| Email | Valid email format | "Enter a valid email address" |
| Password | Min 8, max 128 chars | "Password must be 8–128 characters" |

### Journal entry editor

| Field | Rule | UX behaviour |
|---|---|---|
| Title | Max 255 chars | Counter shown at 200+: `"220 / 255"` below field |
| Content | Max 10,000 chars | Live counter always visible bottom-right of content field: `"1,842 / 10,000"` · turns red (`chronicle-ruby` / `error`) at 9,500+ |
| Tags | Max 10 per entry | "+ add tag" chip hidden when 10 tags reached |
| Tag name | Max 50 chars | Counter shown at 40+: `"47 / 50"` |

### Password change

| Field | Rule | Error message |
|---|---|---|
| Current password | Required | "Enter your current password" |
| New password | Min 8, max 128 chars | "Password must be 8–128 characters" |
| Confirm password | Must match new | "Passwords do not match" |

> **Android implementation:** validate in ViewModel using `StateFlow<FormState>`.
> Validation rules mirror backend `@Size`, `@NotBlank`, `@Email` annotations exactly.
> Save button disabled until content field is not empty (minimum viable entry).

**Register screen:**
- Show hint below password field: "At least 8 characters"
- Hint color changes to teal when `password.length >= 8`
- On 400 from backend: show Snackbar "Password must be at least 8 characters"

**Reset password screen:**
- Same hint on the new password field

## Forgot password flow
1. "Forgot password?" link on Login screen
2. Navigates to Forgot password screen — email input
3. Calls `POST /api/users/password-reset/request { email }`
4. Always shows: "If an account exists for that email, a reset link has been sent."
5. User taps link in email → deep link opens Reset password screen
6. Enters new password + confirm
7. Calls `PUT /api/users/password-reset/confirm { token, newPassword }`
8. Success → Snackbar "Password changed. Please sign in." → navigate to Login
9. Error (expired/invalid token) → "This link is invalid or expired. Request a new one."

**Deep link configuration:** register `bravoscribe://reset-password/{token}` in AndroidManifest

> **Note:** The Notification Service sends password reset emails to the React web URL
> (`{FRONTEND_URL}/reset-password#token={rawToken}`). Users reset their password via
> the web app and then log in again on Android. The `bravoscribe://` deep link is
> registered for potential future use (e.g. if a dedicated mobile reset flow is added)
> but is not triggered by the current email flow..

---

## Offline behaviour
- Entries cached in Room after first fetch
- User can read cached entries offline
- Writes queue locally and sync when connectivity restored
- Show "You're offline" banner when no network detected

---

## Auth flow
1. Login → `POST /api/users/login`
2. `accessToken` + `refreshToken` stored in `EncryptedSharedPreferences`
3. OkHttp `AuthInterceptor` attaches Bearer token to every request
4. OkHttp `TokenRefreshInterceptor` handles 401 — refreshes and retries silently
5. If refresh fails (token expired or revoked):
   - Clear stored tokens (EncryptedSharedPreferences)
   - Navigate to Login screen with `sessionExpired = true` argument
   - Login screen shows subtitle: "Your session has expired. Please sign in again."

```kotlin
// Navigation with argument
navController.navigate("login?sessionExpired=true") {
    popUpTo(0) { inclusive = true }  // clear back stack
}

// Login screen reads argument
val sessionExpired = navBackStackEntry.arguments?.getBoolean("sessionExpired") ?: false

if (sessionExpired) {
    Text(
        text = "Your session has expired. Please sign in again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}
```
6. Logout → clear tokens → navigate to Login

---

## Environment variables (local.properties)
```
API_BASE_URL=http://10.0.2.2:8080       # emulator → localhost
# Production: https://[apim-name].azure-api.net
```

---

## AI Regeneration Prompt
```
Using SYSTEM_SPEC.md and android/SPEC.md, generate the full Android
project scaffold with Jetpack Compose and Material 3 warm theme.
App name: Bravoscribe. Apply the warm color scheme and typography from
the Material 3 theme section — Lora serif for entry titles and body,
Inter/default sans for UI chrome, parchment background colors,
accent brown (#8B4513) as primary color. Include:
- Hilt setup and all DI modules
- Retrofit client with AuthInterceptor and TokenRefreshInterceptor
- Room database with JournalEntry, Tag entities and DAOs
- Compose NavGraph with all routes listed in the Screens section
- Login, Register, ForgotPassword, ResetPassword screens
- Home screen with 3 states: empty (no entry today), editor open blank,
  editor with existing entry — including streak bar with dashed today slot
- Editor screen for past entries: back arrow, date in top bar, purple
  banner "Editing a past entry", Save changes button, no streak bar
- Entry List screen with compact calendar (purple dots), search, export icon
- Entry Detail screen: mood + tags + title + content + Edit button
- Profile screen: 2x2 stats grid, account section, Coming soon notifications
- Export bottom sheet with DownloadManager integration
- Null handling: title null → show first content line, mood null → no emoji,
  tags empty → hide row
- Streak bar component: 7-day window from /entries/dates endpoint,
  dashed today slot when no entry yet, solid when saved
```
