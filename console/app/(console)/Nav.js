'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';

const LINKS = [
  ['/', 'Dashboard'],
  ['/verify', 'Verify a resident'],
  ['/batch', 'Batch verification'],
  ['/audit', 'Audit log'],
  ['/developers', 'Developers'],
  ['/showcase/ikeja-heights', 'Showcase: tenancy'],
];

export default function Nav() {
  const path = usePathname();
  const router = useRouter();

  async function signOut() {
    await fetch('/api/session', { method: 'DELETE' });
    router.replace('/login');
    router.refresh();
  }

  return (
    <>
      <nav className="nav">
        {LINKS.map(([href, label]) => (
          <Link
            key={href}
            href={href}
            className={path === href ? 'active' : ''}
          >
            {label}
          </Link>
        ))}
      </nav>
      <div className="spacer" />
      <div className="nav">
        <a href="#" onClick={(e) => { e.preventDefault(); signOut(); }}>
          Sign out
        </a>
      </div>
    </>
  );
}
