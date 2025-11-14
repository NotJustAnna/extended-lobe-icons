# Icon Patcher

Automated tool to process and patch icons from @lobehub packages with various background treatments.

## Features

- Downloads icons from `@lobehub/icons-static-svg`, `@lobehub/icons-static-png`, and `@lobehub/icons-static-webp`
- Adds black backgrounds to all images in `dark/` folders (saved as `*-bg.*`)
- Adds white backgrounds to all images in `light/` folders (saved as `*-bg.*`)
- Detects single solid colors in `*-color.*` files and creates colored backgrounds (saved as `*-colored-bg.*`)
- Supports both SVG (via XML manipulation) and raster formats (PNG, WEBP, JPG)

## Installation

```bash
# Install patcher dependencies
cd patcher
npm install

# Install upstream packages to track
cd upstream
npm install
cd ..
```

## Usage

```bash
cd patcher
npm run patch
```

This will:
1. Copy all files from the upstream packages to a working directory
2. Process all images in `dark/` and `light/` folders
3. Detect and apply colored backgrounds where applicable
4. Copy the processed files to `../packages/`

## How It Works

### Background Addition

**For dark folders:**
- Original: `dark/icon.svg` → Generated: `dark/icon-bg.svg` (black background)

**For light folders:**
- Original: `light/icon.png` → Generated: `light/icon-bg.png` (white background)

### Colored Backgrounds

When both `icon.ext` and `icon-color.ext` exist:
1. Analyzes `icon-color.ext` to detect if it contains a single solid color
2. If a solid color is found, applies that color as background to `icon.ext`
3. Saves as `icon-colored-bg.ext`

### SVG Handling

SVG files are processed via XML manipulation:
- Backgrounds are added by inserting a `<rect>` element at the beginning
- Colors are detected by parsing `fill` and `stroke` attributes
- Files remain as SVG (no rasterization)

## Directory Structure

```
patcher/
├── index.js           # Main patcher script
├── package.json       # Patcher dependencies
├── upstream/          # Tracked upstream packages
│   └── package.json   # Dependencies on @lobehub packages
├── working/           # Temporary working directory (gitignored)
└── README.md          # This file
```

## Dependabot

Dependabot is configured to track the `upstream/` folder for automatic updates to the icon packages.
