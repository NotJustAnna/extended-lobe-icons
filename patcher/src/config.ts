import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Upstream packages to track and download
 */
export const PACKAGES = [
  '@lobehub/icons-static-svg',
  '@lobehub/icons-static-png',
  '@lobehub/icons-static-webp'
] as const;

/**
 * Working directory for processing
 */
export const WORKING_DIR = path.join(__dirname, '..', 'working');

/**
 * Output directory for patched packages
 */
export const PACKAGES_DIR = path.join(__dirname, '..', '..', 'packages');

/**
 * Upstream dependencies directory
 */
export const UPSTREAM_DIR = path.join(__dirname, '..', 'upstream');

/**
 * Supported raster image extensions
 */
export const RASTER_EXTENSIONS = ['png', 'jpg', 'jpeg', 'webp'] as const;
