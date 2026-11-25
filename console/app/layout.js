import './globals.css';

export const metadata = {
  title: 'LagID Connect',
  description: 'Verify with LagID: consent-based identity for Lagos',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
