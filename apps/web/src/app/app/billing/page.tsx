"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { getUsage, startCheckout, type UsageInfo } from "@/lib/api";
import { loadSession } from "@/lib/session";

function BillingInner() {
  const router = useRouter();
  const params = useSearchParams();
  const [usage, setUsage] = useState<UsageInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadingAction, setLoadingAction] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (params.get("ok") === "1") {
      setNotice("Pagamento concluído. Créditos serão atualizados em instantes.");
    } else if (params.get("cancel") === "1") {
      setNotice("Checkout cancelado.");
    }
  }, [params]);

  useEffect(() => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    getUsage(session.sessionToken)
      .then(setUsage)
      .catch((err) => setError(err instanceof Error ? err.message : "erro"));
  }, [router]);

  async function checkout(pack: "starter" | "pro") {
    const session = loadSession();
    if (!session) {
      return;
    }
    setLoadingAction(true);
    setError(null);
    try {
      const origin = window.location.origin;
      const result = await startCheckout(
        session.sessionToken,
        pack,
        `${origin}/app/billing?ok=1`,
        `${origin}/app/billing?cancel=1`
      );
      window.location.href = result.url;
    } catch (err) {
      setError(err instanceof Error ? err.message : "checkout_error");
      setLoadingAction(false);
    }
  }

  return (
    <AppShell>
      <section className="panel">
        <h1 className="hero-title">Créditos</h1>
        <p className="muted">Saldo atual e pacotes para recarga via Stripe.</p>
        {notice ? (
          <p className="muted" style={{ marginTop: "0.75rem" }}>
            {notice}
          </p>
        ) : null}
        {usage ? (
          <div className="stat-row" style={{ marginTop: "1.25rem" }}>
            <div className="stat">
              <strong>{usage.credits.toLocaleString("pt-BR")}</strong>
              <span className="muted">Disponíveis</span>
            </div>
            <div className="stat">
              <strong>{usage.creditsUsed.toLocaleString("pt-BR")}</strong>
              <span className="muted">Consumidos</span>
            </div>
            <div className="stat">
              <strong>{usage.stripeConfigured ? "sim" : "não"}</strong>
              <span className="muted">Stripe configurado</span>
            </div>
          </div>
        ) : (
          <p className="muted">Carregando...</p>
        )}
        <div className="actions">
          <button
            className="button"
            type="button"
            disabled={loadingAction}
            onClick={() => checkout("starter")}
          >
            Starter — 5.000 créditos
          </button>
          <button
            className="button"
            type="button"
            disabled={loadingAction}
            onClick={() => checkout("pro")}
          >
            Pro — 50.000 créditos
          </button>
        </div>
        {error ? <p className="error">{error}</p> : null}
      </section>
    </AppShell>
  );
}

export default function BillingPage() {
  return (
    <Suspense fallback={<AppShell><p className="muted">Carregando...</p></AppShell>}>
      <BillingInner />
    </Suspense>
  );
}
