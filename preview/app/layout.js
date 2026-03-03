export const metadata = {
  title: 'Auto Clicker - Preview',
  description: 'Interactive preview of Auto Clicker Android app',
}

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body style={{ margin: 0, padding: 0, background: '#0a0a0f' }}>
        {children}
      </body>
    </html>
  )
}
