// ─── PullCode Design Tokens ───────────────────────────────────────────────────
// Single file to change the entire UI color scheme.
// To add a light/dark toggle button: import { toggleTheme } from './theme'
// and call it from any component.

export const colors = {
  dark: {
    // Backgrounds
    bgBase:       '#0d0f12',   // page / outermost bg
    bgSurface:    '#13161b',   // sidebar / card bg
    bgElevated:   '#1a1e26',   // hover, active row
    bgMuted:      '#1e232d',   // subtle section bg

    // Borders
    border:       '#252a35',
    borderFocus:  '#3b82f6',

    // Text
    textPrimary:  '#e8eaf0',
    textSecondary:'#8b92a5',
    textMuted:    '#525968',

    // Accent (blue — matches PullCode brand in screenshot)
    accent:       '#3b82f6',
    accentHover:  '#2563eb',
    accentSubtle: 'rgba(59,130,246,0.12)',

    // Active nav item
    navActiveBg:  '#1e2d45',
    navActiveText:'#60a5fa',

    // Dot indicator (pinned projects)
    dot:          '#6366f1',

    // Profile expand
    profileBg:    '#1a1e26',
  },

  light: {
    bgBase:       '#f1f4f9',
    bgSurface:    '#ffffff',
    bgElevated:   '#e8edf5',
    bgMuted:      '#f5f7fb',

    border:       '#dde2ec',
    borderFocus:  '#3b82f6',

    textPrimary:  '#111827',
    textSecondary:'#6b7280',
    textMuted:    '#9ca3af',

    accent:       '#3b82f6',
    accentHover:  '#2563eb',
    accentSubtle: 'rgba(59,130,246,0.08)',

    navActiveBg:  '#dbeafe',
    navActiveText:'#1d4ed8',

    dot:          '#6366f1',

    profileBg:    '#f5f7fb',
  },
};


export function applyTheme(mode = 'dark') {
  const t = colors[mode];
  const root = document.documentElement;
  Object.entries(t).forEach(([key, val]) => {
    root.style.setProperty(`--color-${key}`, val);
  });
  root.setAttribute('data-theme', mode);
  localStorage.setItem('pc-theme', mode);
}

export function getStoredTheme() {
  return localStorage.getItem('pc-theme') || 'dark';
}

export function toggleTheme() {
  const current = document.documentElement.getAttribute('data-theme') || 'dark';
  applyTheme(current === 'dark' ? 'light' : 'dark');
}
