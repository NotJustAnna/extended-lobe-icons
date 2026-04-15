# Icon Patcher

Downloads LobeHub's published icon packages from npm and generates a fan-out of
pre-processed variants (avatar-fitted, themed backgrounds, brand-colored
backgrounds) for every brand. Used to refresh `../packages/` whenever upstream
publishes a new `@lobehub/icons-static-{png,webp}` release.

## Stack

- **Kotlin 1.9.22** on **JVM 21** (virtual threads for per-brand parallelism).
- **Apache Batik 1.17** (`batik-transcoder`, `batik-codec`) for rendering SVG
  gradients to raster.
- **`java.awt.image` + `javax.imageio`** for raster I/O, compositing, and
  affine transforms.
- **`webp-imageio`** (runtime-only) for WebP read/write.
- **`commons-compress`** for tarball extraction.
- No ImageMagick, no IM4Java, no external binaries.

## Architecture

```
Main.main
├── ShaManifest.loadOrNull(packages/sha.json)          # prior cache, if any
├── Config.prepareWorkingDirectories()                 # wipe & mkdir working/
├── Downloader.run()                                   # npm registry → cache → tarball → working/input/icons/
├── JobScanner.scanJobs(brandFilter, prevManifest, V)  # one ImageProcessingJob per brand dir
├── Thread.ofVirtual().start(job) per brand            # parallel in prod; serial with --dev
│     └── ImageProcessingJob.run()
│           ├── tryReuseCachedOutputs()                # SHA-equal & same patcher version → copy prior output
│           │     (otherwise the nondeterministic pipeline below runs)
│           ├── BrandColorDetector.detect(color file)  # solid / linear gradient / NONE
│           └── per image file:
│                 ├── AvatarFit.apply(image)           # boundary-pixel shrinkwrap → scale-fill
│                 ├── BackgroundGenerator.createSolid / createGradient
│                 ├── Compositor.apply
│                 └── ImageIO.write per suffix: -avatar, -bg, -bg-avatar, -colorbg, -colorbg-avatar
├── copyToPackages()                                   # working/{input,output}/* → packages/
├── generateIndexJson()                                # packages/index.json (file tree)
└── generateShaJson(upstreamPackages)                  # packages/sha.json (cache key)
```

Each brand is its own virtual thread. The only shared state is the
`LinearGradientReconstructor`'s per-instance LAB cache, which lives entirely
within a single job.

## Caching

`packages/sha.json` records, per brand, the SHA-256 of every source file used
to produce the currently-published outputs, plus the patcher version.

On the next run:

1. If a prior `sha.json` exists, it's loaded before `packages/` is touched.
2. For each brand, `ImageProcessingJob.tryReuseCachedOutputs` short-circuits
   iff the recorded patcher version matches the current one **and** every
   source file hashes to its recorded SHA. Outputs are copied byte-for-byte
   from the prior published directory.
3. Otherwise the full pipeline runs.

Bumping `version` in `build.gradle.kts` invalidates every cache entry and
forces a clean regeneration. This is the intended escape hatch when output
semantics change (e.g., suffix renames, algorithmic fixes that affect pixels).

The pipeline is currently **nondeterministic** at the pixel level —
`LinearGradientReconstructor` uses bucketed averaging that depends on sample
order, and `AvatarFit` converges via floating-point shrinkage. This is why
the cache keys on inputs rather than expected output hashes.

## Output variants

For each upstream source file `<brand>/{dark,light}[-color].{png,webp}` the
patcher emits:

| Suffix             | Meaning                                         |
|--------------------|-------------------------------------------------|
| (none)             | upstream source, copied through                  |
| `-avatar`          | ellipse-fitted & scaled to fill the canvas       |
| `-bg`              | composited on a theme-matched solid background   |
| `-bg-avatar`       | avatar variant on theme background               |
| `-colorbg`         | composited on detected brand color/gradient      |
| `-colorbg-avatar`  | avatar variant on brand background               |

Source files whose name tokens include `text`, `brand`, or `cn` are passed
through unmodified (`Config.UPSTREAM_PASSTHROUGH_TOKENS`).

## Building & running

```bash
# Compile only
./gradlew compileKotlin

# Full run (downloads upstream, processes every brand, writes ../packages/)
./gradlew run

# Dev mode: serial (no virtual threads), verbose logging, optionally single brand
./gradlew run --args="--dev claude"

# Multiple brands in production mode (parallel)
./gradlew run --args="claude qwen openai"

# Fat jar
./gradlew fatJar
java -jar build/libs/patcher-2.0.0-all.jar [--dev] [brand...]
```

The patcher writes to `working/` (recreated every run) and `cache/`
(persisted tarballs). Output lands in `../packages/` by default; override
with `-Dpatcher.packagesDir=/some/other/path`.

## Source layout

```
src/main/kotlin/net/notjustanna/patcher/
├── Main.kt                               entry point, orchestration
├── config/Config.kt                      paths, thresholds, dev flag
├── manifest/ShaManifest.kt               sha.json schema + SHA-256 helper
├── models/
│   ├── ImageProcessingJob.kt             per-brand pipeline
│   └── Job.kt                            ColorDetection / DetectionType / ColorStop
├── processors/
│   ├── AvatarFit.kt                      ellipse shrinkwrap + scale-fill
│   ├── BackgroundGenerator.kt            solid & Batik-rendered gradient backgrounds
│   └── Compositor.kt                     foreground-on-background centering
└── utils/
    ├── BrandColorDetector.kt             solid / linear-gradient detection
    ├── Downloader.kt                     npm registry → tarball → working/
    ├── JobScanner.kt                     enumerate brand dirs → jobs
    ├── LabColor.kt                       sRGB ↔ CIE LAB, ΔE₇₆
    └── LinearGradientReconstructor.kt    angle search + stop extraction
```
