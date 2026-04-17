'use client';

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface ThemeContextType {
  theme: 'light' | 'dark';
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<'light' | 'dark'>('light');

  useEffect(() => {
    const saved = localStorage.getItem('app-theme') as 'light' | 'dark' | null;
    if (saved) setTheme(saved);
  }, []);

  const toggleTheme = () => {
    setTheme((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      localStorage.setItem('app-theme', next);
      return next;
    });
  };

  useEffect(() => {
    const root = document.documentElement;

    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }

    const vars =
      theme === 'dark'
        ? {
            background: '#09111f',
            foreground: '#f3f7ff',
            accent: '#3c9ae8',
            surface: '#111a2c',
            'surface-hover': '#17233a',
            muted: '#0f1829',
            border: '#1f3655',
            'border-secondary': '#162947',
            'text-secondary': '#9fb3c8',
          }
        : {
            background: '#eef3fb',
            foreground: '#14213d',
            accent: '#1677ff',
            surface: '#ffffff',
            'surface-hover': '#f8fbff',
            muted: '#f1f5f9',
            border: '#d8e3f0',
            'border-secondary': '#e8eef5',
            'text-secondary': '#5f6c85',
          };

    Object.entries(vars).forEach(([key, value]) => {
      root.style.setProperty(`--${key}`, value);
    });
    root.style.colorScheme = theme;
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
};
