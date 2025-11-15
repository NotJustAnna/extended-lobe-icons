import fs from 'fs-extra';
import path from 'path';
import { PACKAGES, WORKING_DIR, UPSTREAM_DIR } from '../config.js';

/**
 * Copy package files from node_modules to working directory
 */
export async function downloadPackages(): Promise<void> {
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
