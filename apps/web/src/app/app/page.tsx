"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import {
  createApiKey,
  getUsage,
  listApiKeys,
  revokeApiKey,
  type ApiKeyCreated,
  type ApiKeySummary,
  type UsageInfo,
} from "@/lib/api";
import { loadSession } from "@/lib/session";

export default function AppPage() {
  const router = useRouter();
  const [usage, setUsage] = useState<UsageInfo | null>(null);
  const [keys, setKeys] = useState<ApiKeySummary[]>([]);
  const [keyName, setKeyName] = useState("");
  const [createdKey, setCreatedKey] = useState<ApiKeyCreated | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [revoking, setRevoking] = useState<string | null>(null);

  async function refresh(sessionToken: string) {
    const [usageData, keysData] = await Promise.all([
      getUsage(sessionToken),
      listApiKeys(sessionToken),
    ]);
    setUsage(usageData);
    setKeys(keysData);
  }

  useEffect(() => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    refresh(session.sessionToken)
      .catch((err) => setError(err instanceof Error ? err.message : "erro"))
      .finally(() => setLoading(false));
  }, [router]);

  async function onCreateKey(event: FormEvent) {
    event.preventDefault();
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    setError(null);
    setCreatedKey(null);
    try {
      const created = await createApiKey(session.sessionToken, keyName.trim() || undefined);
      setCreatedKey(created);
      setKeyName("");
      await refresh(session.sessionToken);
    } catch (err) {
      setError(err instanceof Error ? err.message : "erro_ao_criar");
    }
  }

  async function onRevoke(id: string) {
    const session = loadSession();
    if (!session) {
      return;
    }
    setRevoking(id);
    setError(null);
    try {
      await revokeApiKey(session.sessionToken, id);
      if (createdKey?.id === id) {
        setCreatedKey(null);
      }
      await refresh(session.sessionToken);
    } catch (err) {
      setError(err instanceof Error ? err.message : "erro_ao_revogar");
    } finally {
      setRevoking(null);
    }
  }

  return (
    <AppShell>
      <section className="panel">
        <h1 className="hero-title">Conta</h1>
        <p className="muted">Créditos, consumo e chaves de API.</p>
        {loading ? (
          <p className="muted" style={{ marginTop: "1rem" }}>
            Carregando...
          </p>
        ) : usage ? (
          <div className="stat-row" style={{ marginTop: "1.25rem" }}>
            <div className="stat">
              <strong>{usage.credits.toLocaleString("pt-BR")}</strong>
              <span className="muted">Créditos disponíveis</span>
            </div>
            <div className="stat">
              <strong>{usage.creditsUsed.toLocaleString("pt-BR")}</strong>
              <span className="muted">Créditos consumidos</span>
            </div>
            {usage.jobsTotal != null ? (
              <div className="stat">
                <strong>{usage.jobsTotal.toLocaleString("pt-BR")}</strong>
                <span className="muted">Jobs totais</span>
              </div>
            ) : null}
          </div>
        ) : null}
      </section>

      <section className="grid-2" style={{ marginTop: "1rem" }}>
        <form className="panel" onSubmit={onCreateKey}>
          <h2 style={{ marginTop: 0, fontWeight: 600 }}>Nova API key</h2>
          <div className="field">
            <label htmlFor="keyName">Nome (opcional)</label>
            <input
              id="keyName"
              value={keyName}
              onChange={(e) => setKeyName(e.target.value)}
              placeholder="produção, staging..."
            />
          </div>
          <button className="button" type="submit">
            Criar key
          </button>
          {createdKey ? (
            <div className="highlight-key mono">
              Copie agora — não será exibida novamente:
              <br />
              <strong>{createdKey.key}</strong>
            </div>
          ) : null}
        </form>

        <section className="panel">
          <h2 style={{ marginTop: 0, fontWeight: 600 }}>API keys</h2>
          {!loading && keys.length === 0 ? <p className="muted">Nenhuma key ainda.</p> : null}
          {keys.length > 0 ? (
            <table className="table">
              <thead>
                <tr>
                  <th>Prefixo</th>
                  <th>Nome</th>
                  <th>Criada</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {keys.map((key) => (
                  <tr key={key.id}>
                    <td className="mono">{key.prefix}</td>
                    <td>{key.name ?? "—"}</td>
                    <td className="muted">{new Date(key.createdAt).toLocaleDateString("pt-BR")}</td>
                    <td>
                      <button
                        type="button"
                        className="button-secondary"
                        disabled={revoking === key.id}
                        onClick={() => onRevoke(key.id)}
                      >
                        {revoking === key.id ? "Revogando..." : "Revogar"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
        </section>
      </section>

      {error ? (
        <p className="error" style={{ marginTop: "1rem" }}>
          {error}
        </p>
      ) : null}
    </AppShell>
  );
}
