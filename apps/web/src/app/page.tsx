"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { requestMagicLink } from "@/lib/api";
import { loadSession } from "@/lib/session";

export default function HomePage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [devLink, setDevLink] = useState<string | null>(null);

  useEffect(() => {
    if (loadSession()) {
      router.replace("/app");
    }
  }, [router]);

  async function onSubmit(event: FormEvent) {
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
          <p className="muted">Informe e-mail e nome para receber um link de acesso.</p>
          <form onSubmit={onSubmit} style={{ marginTop: "1.25rem" }}>
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
              <label htmlFor="name">Nome</label>
              <input
                id="name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Seu nome"
              />
            </div>
            <button className="button" type="submit" disabled={loading}>
              {loading ? "Enviando..." : "Enviar link de acesso"}
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
