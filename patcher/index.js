#!/usr/bin/env node
import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';
import sharp from 'sharp';
import { glob } from 'glob';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PACKAGES = [
  '@lobehub/icons-static-svg',
  '@lobehub/icons-static-png',
  '@lobehub/icons-static-webp'
];

const WORKING_DIR = path.join(__dirname, 'working');
const PACKAGES_DIR = path.join(__dirname, '..', 'packages');
const UPSTREAM_DIR = path.join(__dirname, 'upstream');

/**
 * Copy package files from node_modules to working directory
 */
async function downloadPackages() {
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
        filter: (src) => {
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
 * Parse color from various CSS color formats to RGB
 */
function parseColor(colorStr) {
  if (!colorStr || colorStr === 'none' || colorStr === 'transparent') {
    return null;
  }

  // Hex color (#RGB or #RRGGBB)
  if (colorStr.startsWith('#')) {
    const hex = colorStr.slice(1);
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
  }

  // RGB/RGBA color
  const rgbMatch = colorStr.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (rgbMatch) {
    return {
      r: parseInt(rgbMatch[1]),
      g: parseInt(rgbMatch[2]),
      b: parseInt(rgbMatch[3])
    };
  }

  // Named colors - just support common ones
  const namedColors = {
    'black': { r: 0, g: 0, b: 0 },
    'white': { r: 255, g: 255, b: 255 },
    'red': { r: 255, g: 0, b: 0 },
    'green': { r: 0, g: 128, b: 0 },
    'blue': { r: 0, g: 0, b: 255 }
  };

  return namedColors[colorStr.toLowerCase()] || null;
}

/**
 * Detect if an SVG has a single solid color
 */
async function detectSolidColorSVG(svgPath) {
  try {
    const svgContent = await fs.readFile(svgPath, 'utf-8');
    const colors = new Set();

    // Find all fill and stroke attributes
    const fillMatches = svgContent.matchAll(/fill=["']([^"']+)["']/g);
    const strokeMatches = svgContent.matchAll(/stroke=["']([^"']+)["']/g);
    const styleMatches = svgContent.matchAll(/style=["']([^"']+)["']/g);

    // Process fill attributes
    for (const match of fillMatches) {
      const color = parseColor(match[1]);
      if (color) {
        colors.add(`${color.r},${color.g},${color.b}`);
      }
    }

    // Process stroke attributes
    for (const match of strokeMatches) {
      const color = parseColor(match[1]);
      if (color) {
        colors.add(`${color.r},${color.g},${color.b}`);
      }
    }

    // Process inline styles
    for (const match of styleMatches) {
      const style = match[1];
      const fillStyleMatch = style.match(/fill:\s*([^;]+)/);
      const strokeStyleMatch = style.match(/stroke:\s*([^;]+)/);

      if (fillStyleMatch) {
        const color = parseColor(fillStyleMatch[1].trim());
        if (color) colors.add(`${color.r},${color.g},${color.b}`);
      }
      if (strokeStyleMatch) {
        const color = parseColor(strokeStyleMatch[1].trim());
        if (color) colors.add(`${color.r},${color.g},${color.b}`);
      }
    }

    // If there's only one color, return it
    if (colors.size === 1) {
      const [colorKey] = colors;
      const [r, g, b] = colorKey.split(',').map(Number);
      return { r, g, b };
    }

    return null;
  } catch (error) {
    console.error(`    Error detecting color in SVG ${svgPath}:`, error.message);
    return null;
  }
}

/**
 * Detect if an image has a single solid color
 */
async function detectSolidColor(imagePath) {
  const ext = path.extname(imagePath).toLowerCase();

  // Handle SVG files
  if (ext === '.svg') {
    return await detectSolidColorSVG(imagePath);
  }

  // Handle raster images
  try {
    const image = sharp(imagePath);
    const { data, info } = await image.raw().toBuffer({ resolveWithObject: true });

    const channels = info.channels;
    const colors = new Map();

    // Sample pixels to detect solid color
    for (let i = 0; i < data.length; i += channels) {
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      const a = channels === 4 ? data[i + 3] : 255;

      // Skip fully transparent pixels
      if (a === 0) continue;

      // Skip translucent pixels
      if (a < 255) return null;

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
    console.error(`    Error detecting color in ${imagePath}:`, error.message);
    return null;
  }
}

/**
 * Add background to an SVG by inserting a rect element
 */
async function addBackgroundToSVG(svgPath, backgroundColor, outputPath) {
  try {
    let svgContent = await fs.readFile(svgPath, 'utf-8');

    // Convert RGB to hex color
    const hexColor = `#${backgroundColor.r.toString(16).padStart(2, '0')}${backgroundColor.g.toString(16).padStart(2, '0')}${backgroundColor.b.toString(16).padStart(2, '0')}`;

    // Extract viewBox or width/height from SVG
    const viewBoxMatch = svgContent.match(/viewBox=["']([^"']+)["']/);
    const widthMatch = svgContent.match(/width=["']([^"']+)["']/);
    const heightMatch = svgContent.match(/height=["']([^"']+)["']/);

    let rectAttrs = `fill="${hexColor}"`;

    if (viewBoxMatch) {
      const [x, y, width, height] = viewBoxMatch[1].split(/\s+/);
      rectAttrs = `x="${x}" y="${y}" width="${width}" height="${height}" ${rectAttrs}`;
    } else if (widthMatch && heightMatch) {
      rectAttrs = `width="${widthMatch[1]}" height="${heightMatch[1]}" ${rectAttrs}`;
    } else {
      // Default size if no dimensions found
      rectAttrs = `width="100%" height="100%" ${rectAttrs}`;
    }

    // Insert background rect as first element after opening <svg> tag
    const backgroundRect = `<rect ${rectAttrs}/>`;
    svgContent = svgContent.replace(/(<svg[^>]*>)/, `$1\n  ${backgroundRect}`);

    await fs.writeFile(outputPath, svgContent, 'utf-8');
    return true;
  } catch (error) {
    console.error(`    Error adding background to SVG ${svgPath}:`, error.message);
    return false;
  }
}

/**
 * Add background to an image
 */
async function addBackground(imagePath, backgroundColor, outputPath) {
  const ext = path.extname(imagePath).toLowerCase();

  // Handle SVG files with XML manipulation
  if (ext === '.svg') {
    return await addBackgroundToSVG(imagePath, backgroundColor, outputPath);
  }

  // Handle raster images with sharp
  try {
    const image = sharp(imagePath);
    const metadata = await image.metadata();

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
    console.error(`    Error adding background to ${imagePath}:`, error.message);
    return false;
  }
}

/**
 * Process images in dark/light folders
 */
async function processFolderImages() {
  console.log('🎨 Processing images with backgrounds...');

  // Find all image files
  const imageExtensions = ['png', 'jpg', 'jpeg', 'webp', 'svg'];
  const patterns = imageExtensions.map(ext =>
    path.join(WORKING_DIR, '**', `*.${ext}`)
  );

  const allFiles = [];
  for (const pattern of patterns) {
    const files = await glob(pattern.replace(/\\/g, '/'));
    allFiles.push(...files);
  }

  console.log(`  Found ${allFiles.length} image files`);

  for (const filePath of allFiles) {
    const dir = path.dirname(filePath);
    const dirName = path.basename(dir);
    const fileName = path.basename(filePath);
    const ext = path.extname(fileName);
    const nameWithoutExt = path.basename(fileName, ext);

    // Skip if it's already a processed file
    if (nameWithoutExt.endsWith('-bg') || nameWithoutExt.endsWith('-colored-bg')) {
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

  console.log('✅ Background processing complete\n');
}

/**
 * Process colored backgrounds
 */
async function processColoredBackgrounds() {
  console.log('🎨 Processing colored backgrounds...');

  // Find all image files again
  const imageExtensions = ['png', 'jpg', 'jpeg', 'webp'];
  const patterns = imageExtensions.map(ext =>
    path.join(WORKING_DIR, '**', `*.${ext}`)
  );

  const allFiles = [];
  for (const pattern of patterns) {
    const files = await glob(pattern.replace(/\\/g, '/'));
    allFiles.push(...files);
  }

  // Group files by base name and directory
  const filesByDir = new Map();

  for (const filePath of allFiles) {
    const dir = path.dirname(filePath);
    const dirName = path.basename(dir);
    const fileName = path.basename(filePath);
    const ext = path.extname(fileName);
    const nameWithoutExt = path.basename(fileName, ext);

    // Skip already processed files
    if (nameWithoutExt.endsWith('-bg') || nameWithoutExt.endsWith('-colored-bg')) {
      continue;
    }

    // Only process dark/light folders
    if (dirName !== 'dark' && dirName !== 'light') {
      continue;
    }

    if (!filesByDir.has(dir)) {
      filesByDir.set(dir, new Map());
    }

    const dirFiles = filesByDir.get(dir);

    // Extract base name (remove -color suffix if present)
    let baseName;
    if (nameWithoutExt.endsWith('-color')) {
      baseName = nameWithoutExt.slice(0, -6); // Remove '-color'
      if (!dirFiles.has(baseName)) {
        dirFiles.set(baseName, {});
      }
      dirFiles.get(baseName).colorFile = filePath;
    } else {
      baseName = nameWithoutExt;
      if (!dirFiles.has(baseName)) {
        dirFiles.set(baseName, {});
      }
      dirFiles.get(baseName).mainFile = filePath;
    }
  }

  // Process color combinations
  let processedCount = 0;
  for (const [dir, dirFiles] of filesByDir) {
    for (const [baseName, files] of dirFiles) {
      if (files.mainFile && files.colorFile) {
        console.log(`  🎨 Processing color pair: ${baseName}`);

        // Detect solid color
        const color = await detectSolidColor(files.colorFile);

        if (color) {
          console.log(`    Detected solid color: rgb(${color.r}, ${color.g}, ${color.b})`);

          const mainExt = path.extname(files.mainFile);
          const outputPath = path.join(dir, `${baseName}-colored-bg${mainExt}`);

          await addBackground(files.mainFile, {
            r: color.r,
            g: color.g,
            b: color.b,
            alpha: 1
          }, outputPath);

          console.log(`    ✅ Created: ${path.basename(outputPath)}`);
          processedCount++;
        } else {
          console.log(`    ⏭️  No solid color detected`);
        }
      }
    }
  }

  console.log(`✅ Processed ${processedCount} colored backgrounds\n`);
}

/**
 * Copy working directory to packages/
 */
async function copyToPackages() {
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
async function main() {
  console.log('🚀 Starting icon patcher\n');

  try {
    await downloadPackages();
    await processFolderImages();
    await processColoredBackgrounds();
    await copyToPackages();

    console.log('🎉 Icon patching complete!');
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

main();
