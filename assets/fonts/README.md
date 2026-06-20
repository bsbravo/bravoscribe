# Shared Fonts

Fonts used across all Bravoscribe frontends — React, Angular, and Android.

## Download (one command)

```bash
cd assets/fonts

# Lora — serif font for headings and entry content (Warm theme)
curl -L "https://github.com/google/fonts/raw/main/ofl/lora/Lora%5Bwght%5D.ttf"      -o "Lora-Variable.ttf"
curl -L "https://github.com/google/fonts/raw/main/ofl/lora/Lora%5Bital%2Cwght%5D.ttf" -o "Lora-VariableItalic.ttf"

# Cinzel — serif font for Chronicle theme headings
curl -L "https://github.com/google/fonts/raw/main/ofl/cinzel/Cinzel%5Bwght%5D.ttf"   -o "Cinzel-Variable.ttf"

# Crimson Text — body text for Chronicle dark theme
curl -L "https://github.com/google/fonts/raw/main/ofl/crimsontext/CrimsonText-Regular.ttf" -o "CrimsonText-Regular.ttf"
curl -L "https://github.com/google/fonts/raw/main/ofl/crimsontext/CrimsonText-Italic.ttf"  -o "CrimsonText-Italic.ttf"
```

## Usage per platform

### React / Angular (`frontend-react/src/assets/fonts/`, `frontend-angular/src/assets/fonts/`)

Copy the TTF files into the app's assets folder, then reference in `globals.css`:

```css
@font-face {
  font-family: 'Lora';
  src: url('/assets/fonts/Lora-Variable.ttf') format('truetype');
  font-weight: 100 900;
}

@font-face {
  font-family: 'Lora';
  src: url('/assets/fonts/Lora-VariableItalic.ttf') format('truetype');
  font-weight: 100 900;
  font-style: italic;
}

@font-face {
  font-family: 'Cinzel';
  src: url('/assets/fonts/Cinzel-Variable.ttf') format('truetype');
  font-weight: 100 900;
}
```

### Android (`android/app/src/main/res/font/`)

Copy the TTF files into `res/font/` and reference in Kotlin:

```kotlin
val LoraFontFamily = FontFamily(
    Font(R.font.lora_variable,          FontWeight.Normal),
    Font(R.font.lora_variable,          FontWeight.Medium),
    Font(R.font.lora_variable_italic,   FontWeight.Normal, FontStyle.Italic),
)

val CinzelFontFamily = FontFamily(
    Font(R.font.cinzel_variable, FontWeight.Normal),
    Font(R.font.cinzel_variable, FontWeight.Bold),
)
```

> **Note:** Android resource file names must be lowercase with underscores.
> Rename files to `lora_variable.ttf`, `lora_variable_italic.ttf`, `cinzel_variable.ttf`
> before copying to `res/font/`.

## Font assignments per theme

| Font | Theme | Used for |
|---|---|---|
| Lora | Warm (default) | App name, entry titles, body text |
| Inter | Both | UI labels, buttons, navigation |
| Cinzel | Chronicle (dark) | App name, headings |
| Crimson Text | Chronicle (dark) | Entry body text |

## Licenses

All fonts are open source under the SIL Open Font License (OFL).
Source: [Google Fonts GitHub](https://github.com/google/fonts)
