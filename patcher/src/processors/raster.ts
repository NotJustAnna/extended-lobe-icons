import fs from 'fs-extra';
import path from 'path';
import sharp from 'sharp';
import { glob } from 'glob';
import { WORKING_DIR, RASTER_EXTENSIONS } from '../config.js';
import type { RGBColor, RGBAColor } from '../types.js';
import { rgbToHex } from '../utils/colors.js';

/**
 * Detect if a raster image has a single solid color
 */
export async function detectSolidColor(imagePath: string): Promise<RGBColor | null> {
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
 * Add background to a raster image
 */
export async function addBackground(imagePath: string, backgroundColor: RGBAColor, outputPath: string): Promise<boolean> {
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
export async function processFolderImages(): Promise<void> {
  console.log('🎨 Processing PNG/WEBP images with backgrounds...');

  // Find all raster image files
  const patterns = RASTER_EXTENSIONS.map(ext =>
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
 * Process PNG/WEBP colored backgrounds
 */
export async function processColoredBackgrounds(): Promise<void> {
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
