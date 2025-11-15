/**
 * RGB color representation
 */
export interface RGBColor {
  r: number;
  g: number;
  b: number;
}

/**
 * RGBA color representation with alpha channel
 */
export interface RGBAColor extends RGBColor {
  alpha: number;
}
