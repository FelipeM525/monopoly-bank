import type { PawnColor } from '@/types/api';

export interface PawnColorTheme {
  label: string;
  swatchClass: string;
  ringClass: string;
  badgeClass: string;
}

export const PAWN_COLOR_THEMES: Record<PawnColor, PawnColorTheme> = {
  RED: {
    label: 'Red',
    swatchClass: 'bg-red-600',
    ringClass: 'ring-red-600',
    badgeClass: 'border-red-200 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950/60 dark:text-red-300',
  },
  BLUE: {
    label: 'Blue',
    swatchClass: 'bg-blue-600',
    ringClass: 'ring-blue-600',
    badgeClass: 'border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900 dark:bg-blue-950/60 dark:text-blue-300',
  },
  GREEN: {
    label: 'Green',
    swatchClass: 'bg-emerald-600',
    ringClass: 'ring-emerald-600',
    badgeClass: 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/60 dark:text-emerald-300',
  },
  YELLOW: {
    label: 'Yellow',
    swatchClass: 'bg-amber-400',
    ringClass: 'ring-amber-500',
    badgeClass: 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950/60 dark:text-amber-300',
  },
  PURPLE: {
    label: 'Purple',
    swatchClass: 'bg-purple-600',
    ringClass: 'ring-purple-600',
    badgeClass: 'border-purple-200 bg-purple-50 text-purple-800 dark:border-purple-900 dark:bg-purple-950/60 dark:text-purple-300',
  },
  ORANGE: {
    label: 'Orange',
    swatchClass: 'bg-orange-500',
    ringClass: 'ring-orange-500',
    badgeClass: 'border-orange-200 bg-orange-50 text-orange-800 dark:border-orange-900 dark:bg-orange-950/60 dark:text-orange-300',
  },
};

export const ALL_PAWN_COLORS: PawnColor[] = [
  'RED',
  'BLUE',
  'GREEN',
  'YELLOW',
  'PURPLE',
  'ORANGE',
];
