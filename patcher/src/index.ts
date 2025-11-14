#!/usr/bin/env node
import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';
import sharp from 'sharp';
import { glob } from 'glob';
import { JSDOM } from 'jsdom';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PACKAGES = [
  '@lobehub/icons-static-svg',
  '@lobehub/icons-static-png',
  '@lobehub/icons-static-webp'
] as const;

const WORKING_DIR = path.join(__dirname, '..', 'working');
const PACKAGES_DIR = path.join(__dirname, '..', '..', 'packages');
const UPSTREAM_DIR = path.join(__dirname, '..', 'upstream');

interface RGBColor {
  r: number;
  g: number;
  b: number;
}

interface RGBAColor extends RGBColor {
  alpha: number;
}

/**
 * Copy package files from node_modules to working directory
 */
async function downloadPackages(): Promise<void> {
  console.log('📦 Downloading packages...');

  // Ensure working directory exists
  await fs.ensureDir(WORKING_DIR);

  for (const pkg of PACKAGES) {
    const packageName = pkg.replace('@lobehub/', '');
    const sourcePath = path.join(UPSTREAM_DIR, 'node_modules', pkg);
    const destPath = path.join(WORKING_DIR, packageName);

    console.log(`  Copying ${pkg} to ${packageName}/`);

    if (await fs.pathExists(sourcePath)) {
      await fs.copy(sourcePath, destPath, {
        filter: (src: string) => {
          // Get relative path from the source
          const relative = path.relative(sourcePath, src);

          // Skip nested node_modules and package.json files
          return !relative.includes('node_modules') &&
                 !relative.endsWith('package.json') &&
                 !relative.endsWith('README.md');
        }
      });
    } else {
      console.warn(`  ⚠️  Package ${pkg} not found in node_modules. Run 'npm install' in patcher/upstream/ first.`);
    }
  }

  console.log('✅ Packages downloaded\n');
}

/**
 * Parse hex color to RGB
 */
function hexToRgb(hex: string): RGBColor | null {
  // Remove # if present
  hex = hex.replace('#', '');

  if (hex.length === 3) {
    return {
      r: parseInt(hex[0] + hex[0], 16),
      g: parseInt(hex[1] + hex[1], 16),
      b: parseInt(hex[2] + hex[2], 16)
    };
  } else if (hex.length === 6) {
    return {
      r: parseInt(hex.slice(0, 2), 16),
      g: parseInt(hex.slice(2, 4), 16),
      b: parseInt(hex.slice(4, 6), 16)
    };
  }
  return null;
}

/**
 * RGB to hex
 */
function rgbToHex(r: number, g: number, b: number): string {
  return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
}

/**
 * Extract color from SVG using DOM parsing
 */
async function extractSVGColor(svgPath: string): Promise<RGBColor | null> {
  try {
    const svgContent = await fs.readFile(svgPath, 'utf-8');
    const dom = new JSDOM(svgContent, { contentType: 'image/svg+xml' });
    const document = dom.window.document;

    // Find path elements with fill attribute
    const paths = document.querySelectorAll('path[fill]');
    if (paths.length > 0) {
      const fillColor = paths[0].getAttribute('fill');
      if (fillColor && fillColor.startsWith('#')) {
        return hexToRgb(fillColor);
      }
    }

    return null;
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error(`    Error extracting color from SVG ${svgPath}:`, errorMessage);
    return null;
  }
}

/**
 * Detect if a raster image has a single solid color
 */
async function detectSolidColor(imagePath: string): Promise<RGBColor | null> {
  try {
    const image = sharp(imagePath);
    const { data, info } = await image.raw().toBuffer({ resolveWithObject: true });

    const channels = info.channels;
    const colors = new Map<string, number>();

    // Sample pixels to detect solid color
    for (let i = 0; i < data.length; i += channels) {
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      const a = channels === 4 ? data[i + 3] : 255;

      // Skip fully transparent pixels
      if (a === 0) continue;

      // Skip translucent pixels (allow some tolerance)
      if (a < 250) return null;

      const colorKey = `${r},${g},${b}`;
      colors.set(colorKey, (colors.get(colorKey) || 0) + 1);
    }

    // If there's only one color, return it
    if (colors.size === 1) {
      const [colorKey] = colors.keys();
      const [r, g, b] = colorKey.split(',').map(Number);
      return { r, g, b };
    }

    return null;
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error(`    Error detecting color in ${imagePath}:`, errorMessage);
    return null;
  }
}

/**
 * Create SVG with colored background using DOM manipulation
 */
async function createSVGWithBackground(svgPath: string, backgroundColor: RGBColor, outputPath: string): Promise<boolean> {
  try {
    const svgContent = await fs.readFile(svgPath, 'utf-8');
    const dom = new JSDOM(svgContent, { contentType: 'image/svg+xml' });
    const document = dom.window.document;
    const svg = document.querySelector('svg');

    if (!svg) {
      console.error(`    No SVG element found in ${svgPath}`);
      return false;
    }

    // Get viewBox or dimensions
    const viewBox = svg.getAttribute('viewBox');
    let rectAttrs: Record<string, string> = {};

    if (viewBox) {
      const [x, y, width, height] = viewBox.split(/\s+/);
      rectAttrs = { x, y, width, height };
    } else {
      const width = svg.getAttribute('width') || '100%';
      const height = svg.getAttribute('height') || '100%';
      rectAttrs = { width, height };
    }

    // Create background rectangle
    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    Object.entries(rectAttrs).forEach(([key, value]) => {
      rect.setAttribute(key, value);
    });
    rect.setAttribute('fill', rgbToHex(backgroundColor.r, backgroundColor.g, backgroundColor.b));

    // Insert as first child
    svg.insertBefore(rect, svg.firstChild);

    // Write back
    await fs.writeFile(outputPath, dom.serialize(), 'utf-8');
    return true;
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error(`    Error creating SVG with background ${svgPath}:`, errorMessage);
    return false;
  }
}

/**
 * Add background to a raster image
 */
async function addBackground(imagePath: string, backgroundColor: RGBAColor, outputPath: string): Promise<boolean> {
  try {
    const image = sharp(imagePath);
    const metadata = await image.metadata();

    if (!metadata.width || !metadata.height) {
      console.error(`    Unable to get dimensions for ${imagePath}`);
      return false;
    }

    // Create a background
    const background = sharp({
      create: {
        width: metadata.width,
        height: metadata.height,
        channels: 4,
        background: backgroundColor
      }
    });

    // Composite the image on top of the background
    await background
      .composite([{ input: imagePath }])
      .toFile(outputPath);

    return true;
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error(`    Error adding background to ${imagePath}:`, errorMessage);
    return false;
  }
}

/**
 * Process PNG/WEBP images in dark/light folders
 */
async function processFolderImages(): Promise<void> {
  console.log('🎨 Processing PNG/WEBP images with backgrounds...');

  // Find all raster image files
  const imageExtensions = ['png', 'jpg', 'jpeg', 'webp'];
  const patterns = imageExtensions.map(ext =>
    path.join(WORKING_DIR, '**', `*.${ext}`)
  );

  const allFiles: string[] = [];
  for (const pattern of patterns) {
    const files = await glob(pattern.replace(/\\/g, '/'));
    allFiles.push(...files);
  }

  console.log(`  Found ${allFiles.length} raster image files`);

  for (const filePath of allFiles) {
    const dir = path.dirname(filePath);
    const dirName = path.basename(dir);
    const fileName = path.basename(filePath);
    const ext = path.extname(fileName);
    const nameWithoutExt = path.basename(fileName, ext);

    // Skip if it's already a processed file
    if (nameWithoutExt.endsWith('-bg') || nameWithoutExt.endsWith('-colored-bg') ||
        nameWithoutExt.endsWith('-colored-dark') || nameWithoutExt.endsWith('-colored-light')) {
      continue;
    }

    // Process dark folder images
    if (dirName === 'dark') {
      const outputPath = path.join(dir, `${nameWithoutExt}-bg${ext}`);
      console.log(`  🌑 Adding black background: ${fileName} -> ${path.basename(outputPath)}`);
      await addBackground(filePath, { r: 0, g: 0, b: 0, alpha: 1 }, outputPath);
    }

    // Process light folder images
    if (dirName === 'light') {
      const outputPath = path.join(dir, `${nameWithoutExt}-bg${ext}`);
      console.log(`  ☀️  Adding white background: ${fileName} -> ${path.basename(outputPath)}`);
      await addBackground(filePath, { r: 255, g: 255, b: 255, alpha: 1 }, outputPath);
    }
  }

  console.log('✅ PNG/WEBP background processing complete\n');
}

/**
 * Process SVG colored icons
 */
async function processSVGColoredIcons(): Promise<void> {
  console.log('🎨 Processing SVG colored icons...');

  // Find all -color.svg files in icons/ folder
  const svgPattern = path.join(WORKING_DIR, 'icons-static-svg', 'icons', '*-color.svg');
  const coloredSVGs = await glob(svgPattern.replace(/\\/g, '/'));

  console.log(`  Found ${coloredSVGs.length} colored SVG files`);

  let processedCount = 0;
  for (const svgPath of coloredSVGs) {
    const fileName = path.basename(svgPath);
    const nameWithoutExt = path.basename(fileName, '.svg');
    const baseName = nameWithoutExt.replace('-color', '');
    const dir = path.dirname(svgPath);

    console.log(`  🎨 Processing: ${fileName}`);

    // Extract the color from the SVG
    const color = await extractSVGColor(svgPath);

    if (color) {
      console.log(`    Extracted color: ${rgbToHex(color.r, color.g, color.b)}`);

      // Create dark version (black background)
      const darkPath = path.join(dir, `${baseName}-colored-dark.svg`);
      await createSVGWithBackground(svgPath, { r: 0, g: 0, b: 0 }, darkPath);
      console.log(`    ✅ Created: ${path.basename(darkPath)}`);

      // Create light version (white background)
      const lightPath = path.join(dir, `${baseName}-colored-light.svg`);
      await createSVGWithBackground(svgPath, { r: 255, g: 255, b: 255 }, lightPath);
      console.log(`    ✅ Created: ${path.basename(lightPath)}`);

      processedCount++;
    } else {
      console.log(`    ⏭️  No color extracted`);
    }
  }

  console.log(`✅ Processed ${processedCount} SVG colored icons\n`);
}

/**
 * Process PNG/WEBP colored backgrounds
 */
async function processColoredBackgrounds(): Promise<void> {
  console.log('🎨 Processing PNG/WEBP colored backgrounds...');

  // Find all *-color.png and *-color.webp files in dark/light folders
  const patterns = [
    path.join(WORKING_DIR, '**', 'dark', '*-color.png'),
    path.join(WORKING_DIR, '**', 'dark', '*-color.webp'),
    path.join(WORKING_DIR, '**', 'light', '*-color.png'),
    path.join(WORKING_DIR, '**', 'light', '*-color.webp')
  ];

  const colorFiles: string[] = [];
  for (const pattern of patterns) {
    const files = await glob(pattern.replace(/\\/g, '/'));
    colorFiles.push(...files);
  }

  console.log(`  Found ${colorFiles.length} color variant files`);

  let processedCount = 0;
  for (const colorFile of colorFiles) {
    const dir = path.dirname(colorFile);
    const fileName = path.basename(colorFile);
    const ext = path.extname(fileName);
    const nameWithoutExt = path.basename(fileName, ext);
    const baseName = nameWithoutExt.replace('-color', '');

    // Look for corresponding main file
    const mainFile = path.join(dir, `${baseName}${ext}`);

    if (await fs.pathExists(mainFile)) {
      console.log(`  🎨 Processing color pair: ${baseName}`);

      // Detect solid color from color file
      const color = await detectSolidColor(colorFile);

      if (color) {
        console.log(`    Detected solid color: ${rgbToHex(color.r, color.g, color.b)}`);

        const outputPath = path.join(dir, `${baseName}-colored-bg${ext}`);

        await addBackground(mainFile, {
          r: color.r,
          g: color.g,
          b: color.b,
          alpha: 1
        }, outputPath);

        console.log(`    ✅ Created: ${path.basename(outputPath)}`);
        processedCount++;
      } else {
        console.log(`    ⏭️  No solid color detected in ${fileName}`);
      }
    }
  }

  console.log(`✅ Processed ${processedCount} PNG/WEBP colored backgrounds\n`);
}

/**
 * Copy working directory to packages/
 */
async function copyToPackages(): Promise<void> {
  console.log('📁 Copying to packages/...');

  // Remove existing packages directory
  if (await fs.pathExists(PACKAGES_DIR)) {
    console.log('  🗑️  Removing existing packages/');
    await fs.remove(PACKAGES_DIR);
  }

  // Copy working directory to packages
  console.log('  📋 Copying files...');
  await fs.copy(WORKING_DIR, PACKAGES_DIR);

  console.log('✅ Copy complete\n');
}

/**
 * Main execution
 */
async function main(): Promise<void> {
  console.log('🚀 Starting icon patcher\n');

  try {
    await downloadPackages();
    await processFolderImages();
    await processSVGColoredIcons();
    await processColoredBackgrounds();
    await copyToPackages();

    console.log('🎉 Icon patching complete!');
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

main();
