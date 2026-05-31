"use client";

import { FormEvent, useEffect, useState } from "react";
import { useParams } from "next/navigation";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

export default function RejectSignupPage() {
  const { token } = useParams<{ token: string }>();
  const [label, setLabel] = useState("Loading signup request...");
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    fetch(`${apiBaseUrl}/api/public/signup-approvals/${token}`).then(async (response) => {
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || body.error || "Unable to load signup request.");
      setLabel(body.email ? `Reject request for ${body.email}` : body.message);
    }).catch((exception) => setError(exception.message));
  }, [token]);

  async function reject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const response = await fetch(`${apiBaseUrl}/api/public/signup-approvals/${token}/reject`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason }),
    });
    const body = await response.json();
    if (!response.ok) return setError(body.message || body.error || "Unable to reject signup request.");
    setMessage(body.message);
  }

  return <main className="account-shell"><form className="account-panel account-form" onSubmit={reject}><h1>Reject signup</h1><p>{label}</p><label>Reason sent to applicant<textarea value={reason} onChange={(event) => setReason(event.target.value)} /></label><button className="primary-button">Reject request</button>{message ? <p className="notice success">{message}</p> : null}{error ? <p className="notice error">{error}</p> : null}</form></main>;
}
