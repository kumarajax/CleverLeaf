import "./globals.css";
import { defaultApplicationConfig } from "./applicationConfig";

export const metadata = {
  title: defaultApplicationConfig.applicationName,
  description: "Local-first examination preparation platform",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
