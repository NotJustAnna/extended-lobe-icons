#!/usr/bin/env node
import { downloadPackages } from './processors/download.js';
import { processFolderImages, processColoredBackgrounds } from './processors/raster.js';
import { processSVGColoredIcons } from './processors/svg.js';
import { copyToPackages } from './processors/copy.js';

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
