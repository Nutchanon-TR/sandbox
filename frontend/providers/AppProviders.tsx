'use client';

import { AuthProvider } from './AuthProvider';
import { LayoutProvider } from './LayoutProvider';
import { ThemeProvider } from './ThemeProvider';
import { ConfigProvider } from './ConfigProvider';
import StyledRegistry from './AntdRegistry';
import { NotificationProvider } from './NotificationProvider';
import { LoadingBarProvider } from './LoadingBarProvider';
import { NavigationGuardProvider } from './NavigationGuardProvider';

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <LayoutProvider>
        <ThemeProvider>
          <StyledRegistry>
            <ConfigProvider>
              <NotificationProvider>
                <LoadingBarProvider>
                  {children}
                </LoadingBarProvider>
              </NotificationProvider>
            </ConfigProvider>
          </StyledRegistry>
        </ThemeProvider>
      </LayoutProvider>
    </AuthProvider>
  );
}
