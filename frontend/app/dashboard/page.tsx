"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type Session = {
  email?: string;
  accessToken?: string;
  refreshToken?: string;
};

type JwtPayload = {
  preferred_username?: string;
  email?: string;
  name?: string;
  realm_access?: { roles?: string[] };
  resource_access?: Record<string, { roles?: string[] }>;
};

type MeResponse = {
  subject?: string;
  username?: string;
  email?: string;
  name?: string;
  roles?: string[];
  clientRoles?: Record<string, string[]>;
  issuer?: string;
};

function readStoredSession() {
  return localStorage.getItem("clearleaf.auth");
}

function removeStoredSession() {
  localStorage.removeItem("clearleaf.auth");
}

function decodePayload(token: string): JwtPayload | null {
  const segment = token.split(".")[1];
  if (!segment) return null;
  const base64 = segment.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
  try {
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

export default function DashboardPage() {
  const router = useRouter();
  const [session, setSession] = useState<Session | null>(null);
  const [payload, setPayload] = useState<JwtPayload | MeResponse | null>(null);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

  useEffect(() => {
    const stored = readStoredSession();
    if (!stored) {
      router.replace("/account");
      return;
    }
    try {
      const parsed = JSON.parse(stored) as Session;
      if (!parsed.accessToken) {
        router.replace("/account");
        return;
      }
      setSession(parsed);
      setPayload(decodePayload(parsed.accessToken));
      fetch(`${apiBaseUrl}/api/me`, {
        headers: { Authorization: `Bearer ${parsed.accessToken}` },
      })
        .then(async (response) => {
          const body = await response.json().catch(() => ({}));
          if (!response.ok) {
            throw new Error(body.error || `Request failed with ${response.status}`);
          }
          setPayload(body);
          setStatus("Session verified against protected backend route.");
        })
        .catch((exception) => {
          removeStoredSession();
          setError(exception instanceof Error ? exception.message : "Session expired.");
          router.replace("/account");
        });
    } catch {
      removeStoredSession();
      router.replace("/account");
    }
  }, [apiBaseUrl, router]);

  function signOut() {
    removeStoredSession();
    router.replace("/account");
  }

  const roles = (() => {
    if (!payload) return [];
    if ("roles" in payload) return payload.roles ?? [];
    const jwtPayload = payload as JwtPayload;
    return jwtPayload.realm_access?.roles ?? [];
  })();
  const isAdmin = roles.includes("administrator");
  const clientRoles = (() => {
    if (!payload) return null;
    if ("clientRoles" in payload) return payload.clientRoles ?? null;
    const jwtPayload = payload as JwtPayload;
    if (jwtPayload.resource_access) {
      return Object.fromEntries(Object.entries(jwtPayload.resource_access).map(([client, details]) => [client, details.roles ?? []]));
    }
    return null;
  })();
  const displayName = (() => {
    if (!payload) return session?.email ?? "Signed in user";
    if ("preferred_username" in payload) {
      return payload.name ?? payload.preferred_username ?? session?.email ?? "Signed in user";
    }
    return payload.name ?? session?.email ?? "Signed in user";
  })();

  return (
    <main className="account-shell">
      <section className="account-panel">
        <a className="back-link" href="/account">← Back to account</a>
        <div className="eyebrow">ClearLeaf Dashboard</div>
        <h1>Welcome, {displayName}</h1>
        <p className="lede">This page shows the user session and the roles returned by Keycloak.</p>

        <div className="session-card">
          <div><strong>Email</strong><span>{session?.email ?? payload?.email ?? "Unknown"}</span></div>
          <div><strong>Role view</strong><span>{roles.includes("student") ? "Student" : "User"}</span></div>
          <div><strong>Client roles</strong><span>{clientRoles ? Object.entries(clientRoles).map(([client, list]) => `${client}: ${list.join(", ") || "none"}`).join(" | ") : "None"}</span></div>
        </div>

        <div className="practice-callout">
          <div>
            <strong>Practice mode</strong>
            <span>Start a sample test and score answers in the browser.</span>
          </div>
          <a className="primary-button" href="/practice">Take test</a>
        </div>

        <div className="dashboard-actions">
          <a className="primary-button" href="/account">Open account page</a>
          {isAdmin ? <a className="secondary-button" href="/admin">Open admin console</a> : null}
          <button type="button" className="secondary-button" onClick={signOut}>Sign out</button>
        </div>
        {status ? <p className="notice success">{status}</p> : null}
        {error ? <p className="notice error">{error}</p> : null}
      </section>
    </main>
  );
}
