import "./globals.css";

export const metadata = {
  title: "ClearLeaf",
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
