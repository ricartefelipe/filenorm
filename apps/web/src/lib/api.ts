import type { AccountSession } from "@/lib/session";

export function resolveApiBase(): string {
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (configured === "same-origin" || configured === "") {
    if (typeof window !== "undefined") {
      return window.location.origin;
    }
    return "";
  }
  if (configured == null) {
    return "http://localhost:8080";
  }
  return configured.replace(/\/$/, "");
}

export type ApiKeySummary = {
  id: string;
  prefix: string;
  name?: string | null;
  createdAt: string;
  lastUsedAt?: string | null;
};

export type ApiKeyCreated = ApiKeySummary & {
  key: string;
};

export type UsageInfo = {
  credits: number;
  creditsUsed: number;
  jobsTotal?: number;
  jobsSucceeded?: number;
  jobsFailed?: number;
  stripeConfigured?: boolean;
};

export type JobSummary = {
  id: string;
  status: string;
  format?: string;
  fileName?: string;
  creditsCharged?: number;
  createdAt: string;
  finishedAt?: string | null;
};

type UsageApiResponse = {
  credits?: number;
  creditsBalance?: number;
  creditsUsed?: number;
  jobsTotal?: number;
  jobsSucceeded?: number;
  jobsFailed?: number;
  stripeConfigured?: boolean;
  recentLedger?: Array<{ delta?: number }>;
};

type ApiKeyApiResponse = {
  id: string;
  prefix?: string;
  keyPrefix?: string;
  name?: string | null;
  createdAt: string;
  lastUsedAt?: string | null;
  apiKey?: string;
  key?: string;
};

type JobApiResponse = {
  id: string;
  status: string;
  format?: string;
  fileName?: string;
  originalFilename?: string;
  creditsCharged?: number;
  createdAt: string;
  finishedAt?: string | null;
};

function authHeaders(sessionToken: string): HeadersInit {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${sessionToken}`,
  };
}

async function parse<T>(response: Response): Promise<T> {
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) {
    const error = typeof data.error === "string" ? data.error : "request_failed";
    throw new Error(error);
  }
  return data as T;
}

function mapUsage(data: UsageApiResponse): UsageInfo {
  const credits = data.credits ?? data.creditsBalance ?? 0;
  const creditsUsed =
    data.creditsUsed ??
    (data.recentLedger ?? [])
      .filter((entry) => typeof entry.delta === "number" && entry.delta < 0)
      .reduce((sum, entry) => sum + Math.abs(entry.delta ?? 0), 0);
  return {
    credits,
    creditsUsed,
    jobsTotal: data.jobsTotal,
    jobsSucceeded: data.jobsSucceeded,
    jobsFailed: data.jobsFailed,
    stripeConfigured: data.stripeConfigured,
  };
}

function mapApiKey(data: ApiKeyApiResponse): ApiKeySummary {
  return {
    id: data.id,
    prefix: data.prefix ?? data.keyPrefix ?? "",
    name: data.name,
    createdAt: data.createdAt,
    lastUsedAt: data.lastUsedAt,
  };
}

function mapApiKeyCreated(data: ApiKeyApiResponse): ApiKeyCreated {
  return {
    ...mapApiKey(data),
    key: data.key ?? data.apiKey ?? "",
  };
}

function mapJob(data: JobApiResponse): JobSummary {
  return {
    id: data.id,
    status: data.status,
    format: data.format,
    fileName: data.fileName ?? data.originalFilename,
    creditsCharged: data.creditsCharged,
    createdAt: data.createdAt,
    finishedAt: data.finishedAt,
  };
}

export async function requestMagicLink(
  email: string,
  name: string
): Promise<{ sent: boolean; email: string; magicLink?: string }> {
  const response = await fetch(`${resolveApiBase()}/v1/auth/magic-link`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, name }),
  });
  return parse(response);
}

export async function verifyMagicLink(token: string): Promise<AccountSession> {
  const response = await fetch(`${resolveApiBase()}/v1/auth/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  });
  const data = await parse<AccountSession & { creditsBalance?: number }>(response);
  return {
    accountId: data.accountId,
    email: data.email,
    name: data.name,
    credits: data.credits ?? data.creditsBalance,
    sessionToken: data.sessionToken,
  };
}

export async function getMe(sessionToken: string): Promise<AccountSession> {
  const response = await fetch(`${resolveApiBase()}/v1/auth/me`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  const data = await parse<AccountSession & { creditsBalance?: number }>(response);
  return {
    accountId: data.accountId,
    email: data.email,
    name: data.name,
    credits: data.credits ?? data.creditsBalance,
    sessionToken,
  };
}

export async function listApiKeys(sessionToken: string): Promise<ApiKeySummary[]> {
  const response = await fetch(`${resolveApiBase()}/v1/account/api-keys`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  const data = await parse<ApiKeyApiResponse[]>(response);
  return data.map(mapApiKey);
}

export async function createApiKey(
  sessionToken: string,
  name?: string
): Promise<ApiKeyCreated> {
  const response = await fetch(`${resolveApiBase()}/v1/account/api-keys`, {
    method: "POST",
    headers: authHeaders(sessionToken),
    body: JSON.stringify(name ? { name } : {}),
  });
  return mapApiKeyCreated(await parse<ApiKeyApiResponse>(response));
}

export async function revokeApiKey(sessionToken: string, id: string): Promise<void> {
  const response = await fetch(`${resolveApiBase()}/v1/account/api-keys/${id}`, {
    method: "DELETE",
    headers: authHeaders(sessionToken),
  });
  await parse(response);
}

export async function getUsage(sessionToken: string): Promise<UsageInfo> {
  const response = await fetch(`${resolveApiBase()}/v1/account/usage`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  return mapUsage(await parse<UsageApiResponse>(response));
}

export async function listJobs(sessionToken: string): Promise<JobSummary[] | null> {
  const response = await fetch(`${resolveApiBase()}/v1/account/jobs`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  if (response.status === 404) {
    return null;
  }
  const data = await parse<JobApiResponse[]>(response);
  return data.map(mapJob);
}

export async function startCheckout(
  sessionToken: string,
  pack: "starter" | "pro",
  successUrl: string,
  cancelUrl: string
): Promise<{ url: string }> {
  const response = await fetch(`${resolveApiBase()}/v1/billing/checkout`, {
    method: "POST",
    headers: authHeaders(sessionToken),
    body: JSON.stringify({ pack, successUrl, cancelUrl }),
  });
  return parse<{ url: string }>(response);
}
