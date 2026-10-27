"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { listJobs, resolveApiBase, type JobSummary } from "@/lib/api";
import { loadSession } from "@/lib/session";

const BALANCE_LABEL: Record<string, string> = {
  verified: "fechou",
  mismatch: "não fechou",
  unavailable: "sem saldo no arquivo",
};

const UPLOAD_CURL = `curl -X POST "$API_BASE/v1/jobs" \\
  -H "Authorization: Bearer fn_live_SUA_KEY" \\
  -F "file=@extrato.ofx" \\
  -F "format=auto"`;

export default function JobsPage() {
  const router = useRouter();
  const [jobs, setJobs] = useState<JobSummary[] | null>(null);
  const [jobsSupported, setJobsSupported] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    listJobs(session.sessionToken)
      .then((data) => {
        if (data === null) {
          setJobsSupported(false);
          setJobs([]);
        } else {
          setJobs(data);
        }
      })
      .catch((err) => setError(err instanceof Error ? err.message : "erro"))
      .finally(() => setLoading(false));
  }, [router]);

  const apiBase = resolveApiBase() || "http://localhost:8080";
  const curlExample = UPLOAD_CURL.replace("$API_BASE", apiBase);

  return (
    <AppShell>
      <section className="panel">
        <h1 className="hero-title">Jobs</h1>
        <p className="muted">
          {jobsSupported
            ? "Jobs recentes da sua conta."
            : "Listagem indisponível — use a API para enviar arquivos."}
        </p>
      </section>

      {!jobsSupported || (jobs !== null && jobs.length === 0 && !loading) ? (
        <section className="panel">
          <h2 style={{ marginTop: 0, fontWeight: 600 }}>Enviar arquivo</h2>
          <p className="muted">
            Faça upload via API key. O job processa OFX, CSV, CNAB240 (retorno de cobrança) ou PDF de
            extrato com texto. Linhas recusadas ficam em <code>GET /v1/jobs/&#123;id&#125;/issues</code> e o
            job aparece como <code>partial</code>.
          </p>
          <pre className="code">{curlExample}</pre>
        </section>
      ) : null}

      {jobsSupported && jobs !== null && jobs.length > 0 ? (
        <section className="panel">
          <table className="table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Status</th>
                <th>Formato</th>
                <th>Eventos</th>
                <th>Rejeitados</th>
                <th>Fechamento</th>
                <th>Créditos</th>
                <th>Criado</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.id}>
                  <td className="mono">{job.id.slice(0, 8)}…</td>
                  <td>
                    <span className={`badge ${job.status}`}>{job.status}</span>
                    {job.errorCode ? <span className="muted"> {job.errorCode}</span> : null}
                  </td>
                  <td>{job.format ?? "—"}</td>
                  <td>{job.eventCount ?? "—"}</td>
                  <td>{job.rejectedCount ?? "—"}</td>
                  <td>
                    {job.balanceStatus ? (
                      <span className={`badge balance-${job.balanceStatus}`}>
                        {BALANCE_LABEL[job.balanceStatus] ?? job.balanceStatus}
                      </span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td>{job.creditsCharged ?? "—"}</td>
                  <td className="muted">
                    {new Date(job.createdAt).toLocaleString("pt-BR")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}

      {loading ? <p className="muted">Carregando...</p> : null}
      {error ? <p className="error">{error}</p> : null}
    </AppShell>
  );
}
