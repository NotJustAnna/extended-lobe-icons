# Extended Lobe Icons

An extended collection of AI brand icons with multiple variants, built on top of [LobeHub's icon packages](https://github.com/lobehub/lobe-icons). A huge thank you to the LobeHub team for their excellent work on the original icon set! 🙏

This project extends the original icons by generating multiple processed variants optimized for different use cases: dark/light themes, with/without backgrounds, avatar-fitted versions, and more.

## 🎨 Generated Icon Variants

Each brand icon is processed into multiple variants for maximum flexibility. Here are the types generated (using Claude as an example):

| Variant | Description | Example |
|---------|-------------|---------|
| **light.png** / **light.webp** | Standard icon for light mode | ![light](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light.png?raw=true) |
| **dark.png** / **dark.webp** | Standard icon for dark mode | ![dark](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark.png?raw=true) |
| **light-avatarfit.png** / **light-avatarfit.webp** | Avatar-fitted icon for light mode | ![light-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-avatarfit.png?raw=true) |
| **dark-avatarfit.png** / **dark-avatarfit.webp** | Avatar-fitted icon for dark mode | ![dark-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-avatarfit.png?raw=true) |
| **light-bg.png** / **light-bg.webp** | Icon with background for light mode | ![light-bg](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-bg.png?raw=true) |
| **dark-bg.png** / **dark-bg.webp** | Icon with background for dark mode | ![dark-bg](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-bg.png?raw=true) |
| **light-bg-avatarfit.png** / **light-bg-avatarfit.webp** | Avatar-fitted with background (light) | ![light-bg-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-bg-avatarfit.png?raw=true) |
| **dark-bg-avatarfit.png** / **dark-bg-avatarfit.webp** | Avatar-fitted with background (dark) | ![dark-bg-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-bg-avatarfit.png?raw=true) |
| **light-color.png** / **light-color.webp** | Colorized icon for light mode | ![light-color](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-color.png?raw=true) |
| **dark-color.png** / **dark-color.webp** | Colorized icon for dark mode | ![dark-color](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-color.png?raw=true) |
| **light-color-avatarfit.png** / **light-color-avatarfit.webp** | Colorized avatar-fitted (light) | ![light-color-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-color-avatarfit.png?raw=true) |
| **dark-color-avatarfit.png** / **dark-color-avatarfit.webp** | Colorized avatar-fitted (dark) | ![dark-color-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-color-avatarfit.png?raw=true) |
| **light-colorbg.png** / **light-colorbg.webp** | Colorized with background (light) | ![light-colorbg](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-colorbg.png?raw=true) |
| **dark-colorbg.png** / **dark-colorbg.webp** | Colorized with background (dark) | ![dark-colorbg](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-colorbg.png?raw=true) |
| **light-colorbg-avatarfit.png** / **light-colorbg-avatarfit.webp** | Colorized bg + avatar-fitted (light) | ![light-colorbg-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-colorbg-avatarfit.png?raw=true) |
| **dark-colorbg-avatarfit.png** / **dark-colorbg-avatarfit.webp** | Colorized bg + avatar-fitted (dark) | ![dark-colorbg-avatarfit](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-colorbg-avatarfit.png?raw=true) |
| **light-text.png** / **light-text.webp** | Text variant for light mode | ![light-text](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/light-text.png?raw=true) |
| **dark-text.png** / **dark-text.webp** | Text variant for dark mode | ![dark-text](https://github.com/NotJustAnna/extended-lobe-icons/blob/main/packages/icons/claude/dark-text.png?raw=true) |

All variants are available in both PNG and WebP formats for optimal performance.

## 🚀 Usage via GitHub CDN

You can use these icons directly in your projects via GitHub's raw content CDN:

```html
<!-- Standard icon -->
<img src="https://raw.githubusercontent.com/NotJustAnna/extended-lobe-icons/main/packages/icons/claude/light.png" alt="Claude" />

<!-- Avatar-fitted with background for dark mode -->
<img src="https://raw.githubusercontent.com/NotJustAnna/extended-lobe-icons/main/packages/icons/claude/dark-bg-avatarfit.webp" alt="Claude" />
```

### URL Pattern

```
https://raw.githubusercontent.com/NotJustAnna/extended-lobe-icons/main/packages/icons/{brand}/{variant}.{format}
```

- **{brand}**: Brand name (e.g., `claude`, `openai`, `anthropic`)
- **{variant}**: One of the variants listed above (e.g., `light`, `dark-avatarfit`, `light-bg`)
- **{format}**: Either `png` or `webp`

### Example in React/JSX

```jsx
const BrandIcon = ({ brand, variant = 'light', format = 'webp' }) => {
  const url = `https://raw.githubusercontent.com/NotJustAnna/extended-lobe-icons/main/packages/icons/${brand}/${variant}.${format}`;
  return <img src={url} alt={brand} />;
};

// Usage
<BrandIcon brand="claude" variant="dark-avatarfit" />
```

## 🛠️ Building from Source

This project includes a Kotlin-based patcher that processes the original LobeHub icons:

```bash
cd patcher
./gradlew build
java -jar build/libs/patcher-1.0.0-all.jar
```

The patcher will:
1. Download the latest icons from LobeHub
2. Process them into multiple variants
3. Generate output in `packages/icons/`
4. Create an `index.json` with the complete file structure

## 📦 Available Brands

See the [packages/icons/](packages/icons/) directory for the complete list of available brand icons.

## 📄 License

This project extends [LobeHub's lobe-icons](https://github.com/lobehub/lobe-icons). Please refer to their repository for licensing information regarding the original icons.

## 🙌 Credits

- **[LobeHub](https://github.com/lobehub)** for the original icon collection
- All brand owners for their respective logos and trademarks