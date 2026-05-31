"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

export default function ApproveSignupPage() {
  const { token } = useParams<{ token: string }>();
  const [message, setMessage] = useState("Approving signup request...");
  const [error, setError] = useState("");

  useEffect(() => {
    fetch(`${apiBaseUrl}/api/public/signup-approvals/${token}/approve`, { method: "POST" })
      .then(async (response) => {
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || body.error || "Unable to approve signup request.");
        setMessage(body.message);
      })
      .catch((exception) => setError(exception.message));
  }, [token]);

  return <main className="account-shell"><section className="account-panel"><h1>Signup approval</h1>{error ? <p className="notice error">{error}</p> : <p className="notice success">{message}</p>}</section></main>;
}
