'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';

interface LoadingContextType {
  isLoading: boolean;
  setIsLoading: (loading: boolean) => void;
}

const LoadingContext = createContext<LoadingContextType | null>(null);

export const useLoadingContext = () => {
  const ctx = useContext(LoadingContext);
  if (!ctx) throw new Error('useLoadingContext must be used within LoadingBarProvider');
  return ctx;
};

export function LoadingBarProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [visible, setVisible] = useState(true);
  const [exiting, setExiting] = useState(false);
  const [manualLoading, setManualLoading] = useState(false);

  useEffect(() => {
    const finish = () => setReady(true);
    if (document.readyState === 'complete') {
      finish();
      return;
    }
    window.addEventListener('load', finish);
    return () => window.removeEventListener('load', finish);
  }, []);

  useEffect(() => {
    if (!ready) return;
    const reduced =
      typeof window !== 'undefined' &&
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reduced) {
      setVisible(false);
      return;
    }

    setExiting(true);
    const t = setTimeout(() => setVisible(false), 300);
    return () => clearTimeout(t);
  }, [ready]);

  return (
    <LoadingContext.Provider value={{ isLoading: manualLoading, setIsLoading: setManualLoading }}>
      {children}
      {(visible || manualLoading) && (
        <div
          aria-hidden={ready}
          role="status"
          aria-live="polite"
          aria-busy={!ready}
          className={`fixed inset-0 z-[9999] flex items-center justify-center bg-surface/95 backdrop-blur-sm transition-opacity duration-300 ${exiting ? 'opacity-0' : 'opacity-100'}`}
        >
          <div className="flex flex-col items-center gap-4 p-6">
            <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500" />
            <span className="text-sm text-text-secondary">Loading...</span>
          </div>
        </div>
      )}
    </LoadingContext.Provider>
  );
}
