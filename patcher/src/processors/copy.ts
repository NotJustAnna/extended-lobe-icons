import fs from 'fs-extra';
import { WORKING_DIR, PACKAGES_DIR } from '../config.js';

/**
 * Copy working directory to packages/
 */
export async function copyToPackages(): Promise<void> {
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
