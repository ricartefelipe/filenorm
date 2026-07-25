"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { clearSession, loadSession } from "@/lib/session";

export function AppShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [email, setEmail] = useState<string | null>(null);

  useEffect(() => {
    const session = loadSession();
    setEmail(session?.email ?? null);
  }, []);

  function onLogout() {
    clearSession();
    router.push("/");
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link href="/app" className="brand">
          File<span>Norm</span>
        </Link>
        <nav className="nav">
          <Link href="/app">Conta</Link>
          <Link href="/app/jobs">Jobs</Link>
          <Link href="/app/billing">Créditos</Link>
          {email ? (
            <button type="button" className="button-secondary" onClick={onLogout}>
              Sair ({email})
            </button>
          ) : null}
        </nav>
      </header>
      {children}
    </div>
  );
}
