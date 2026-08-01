"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { requestMagicLink } from "@/lib/api";
import { loadSession } from "@/lib/session";

function authErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof Error)) {
    return fallback;
  }

  switch (error.message) {
    case "falha_ao_enviar":
      return "Não foi possível enviar o link de acesso. Tente novamente.";
    default:
      return fallback;
  }
}

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
      setError(authErrorMessage(err, "Não foi possível enviar o link de acesso. Tente novamente."));
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
          <p className="muted">Receba um link mágico no e-mail para acessar.</p>
          <form onSubmit={onMagicLink} style={{ marginTop: "1.25rem" }}>
            <div className="field">
              <label htmlFor="email">E-mail</label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="voce@empresa.com"
                autoComplete="email"
              />
            </div>
            <div className="field">
              <label htmlFor="name">Nome</label>
              <input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Para magic link"
                autoComplete="off"
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
