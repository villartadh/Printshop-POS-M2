import type { PlatformPreviewMode } from './types.ts';
export type LayoutTier = 'desktop' | 'tablet' | 'mobile';
export function resolveLayoutTier(width: number, preview: PlatformPreviewMode): LayoutTier {
  const actual = width < 768 ? 'mobile' : width < 1024 ? 'tablet' : 'desktop';
  if (preview === 'mobile' || actual === 'mobile') return 'mobile';
  if (preview === 'tablet' || actual === 'tablet') return 'tablet';
  return 'desktop';
}
