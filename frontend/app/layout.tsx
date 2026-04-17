import "./globals.css";
import { AppProviders } from "@/providers/AppProviders";
import Sidebar from "@/components/SideBar";
import { NavigationGuardProvider } from "@/providers/NavigationGuardProvider";

export const metadata = {
  title: 'Sandbox App',
  description: 'Where Your Greatest Projects Become Reality.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body suppressHydrationWarning>
        <AppProviders>
          <Sidebar>
            <NavigationGuardProvider>
              {children}
            </NavigationGuardProvider>
          </Sidebar>
        </AppProviders>
      </body>
    </html>
  );
}
