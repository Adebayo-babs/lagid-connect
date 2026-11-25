import { redirect } from 'next/navigation';
import { apiFetch, getKey } from '@/lib/api';
import Nav from './Nav';

// Every console page needs a signed-in business
export default async function ConsoleLayout({ children }) {
  const key = await getKey();
  if (!key) redirect('/login');

  const me = await apiFetch('/v1/me', { key }).catch(() => null);
  if (!me?.ok) redirect('/login');

  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">LagID <span>Connect</span></div>
        <div className="biz">{me.data.name}</div>
        <Nav />
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
