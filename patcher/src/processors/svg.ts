import fs from 'fs-extra';
import path from 'path';
import { glob } from 'glob';
import { JSDOM } from 'jsdom';
import { WORKING_DIR } from '../config.js';
import type { RGBColor } from '../types.js';
import { hexToRgb, rgbToHex } from '../utils/colors.js';

/**
 * Extract color from SVG using DOM parsing
 */
export async function extractSVGColor(svgPath: string): Promise<RGBColor | null> {
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
 * Create SVG with colored background using DOM manipulation
 */
export async function createSVGWithBackground(svgPath: string, backgroundColor: RGBColor, outputPath: string): Promise<boolean> {
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
 * Process SVG colored icons
 */
export async function processSVGColoredIcons(): Promise<void> {
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
