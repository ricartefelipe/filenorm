"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { loginWithPassword, requestMagicLink } from "@/lib/api";
import { loadSession, saveSession } from "@/lib/session";

export default function HomePage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [devLink, setDevLink] = useState<string | null>(null);

  useEffect(() => {
    if (loadSession()) {
      router.replace("/app");
    }
  }, [router]);

  async function onMagicLink(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setSent(false);
    setDevLink(null);
    try {
      const result = await requestMagicLink(email.trim(), name.trim() || email.trim());
      setSent(true);
      if (result.magicLink) {
        setDevLink(result.magicLink);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_ao_enviar");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app-shell">
      <div className="login-layout">
        <section>
          <h1 className="brand-hero">
            File<span>Norm</span>
          </h1>
          <p className="tagline muted">
            Envie OFX, CSV, CNAB ou PDF do banco. Receba eventos financeiros normalizados — sem
            parser por instituição no seu código.
          </p>
        </section>

        <section className="panel">
          <h2 className="hero-title">Entrar</h2>
          <p className="muted">E-mail/senha TotalRecall ou link mágico.</p>
          <form
            onSubmit={async (event) => {
              event.preventDefault();
              if (!password.trim()) {
                await onMagicLink(event);
                return;
              }
              setLoading(true);
              setError(null);
              try {
                const session = await loginWithPassword(email.trim(), password);
                saveSession(session);
                router.replace("/app");
              } catch (err) {
                setError(err instanceof Error ? err.message : "falha_no_login");
              } finally {
                setLoading(false);
              }
            }}
            style={{ marginTop: "1.25rem" }}
          >
            <div className="field">
              <label htmlFor="email">E-mail</label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="voce@empresa.com"
              />
            </div>
            <div className="field">
              <label htmlFor="password">Senha TotalRecall (opcional)</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="trp_…"
                autoComplete="current-password"
              />
            </div>
            <div className="field">
              <label htmlFor="name">Nome</label>
              <input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Para magic link"
              />
            </div>
            <button className="button" type="submit" disabled={loading}>
              {loading ? "Entrando..." : password ? "Entrar com senha" : "Enviar link de acesso"}
            </button>
            {sent ? (
              <p className="muted" style={{ marginTop: "0.9rem" }}>
                Link enviado. Confira sua caixa de entrada.
              </p>
            ) : null}
            {devLink ? (
              <div className="highlight-key mono">
                Dev: <a href={devLink}>{devLink}</a>
              </div>
            ) : null}
            {error ? <p className="error">{error}</p> : null}
          </form>
        </section>
      </div>
    </div>
  );
}
