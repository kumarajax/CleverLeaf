"use client";

import { FormEvent, useEffect, useState } from "react";
import { apiBaseUrl } from "../applicationConfig";
import { useApplicationConfig } from "../useApplicationConfig";

const legalVersion = process.env.NEXT_PUBLIC_LEGAL_CURRENT_VERSION ?? "2026-05-30";
const approvalRequired = (process.env.NEXT_PUBLIC_USER_CREATION_APPROVAL_REQUIRED ?? "Y").toLowerCase() !== "n";
const authStorageKeys = ["clearleaf.auth"];

function readStoredSession() {
  return localStorage.getItem("clearleaf.auth");
}

function removeStoredSession() {
  for (const key of authStorageKeys) {
    localStorage.removeItem(key);
  }
}

function captcha() {
  return Math.random().toString(36).slice(2, 8).toUpperCase();
}

async function message(response: Response) {
  try {
    const body = await response.json();
    return body.message || body.error || `Request failed with ${response.status}`;
  } catch {
    return `Request failed with ${response.status}`;
  }
}

type AccountAccessPanelProps = {
  showBackLink?: boolean;
};

export function AccountAccessPanel({ showBackLink = true }: AccountAccessPanelProps) {
  const { applicationName } = useApplicationConfig();
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [captchaCode, setCaptchaCode] = useState(captcha);
  const [captchaInput, setCaptchaInput] = useState("");
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [signedInEmail, setSignedInEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const stored = readStoredSession();
    if (!stored) return;
    try {
      const session = JSON.parse(stored) as { email?: string; accessToken?: string };
      if (session.email) setSignedInEmail(session.email);
      window.location.replace("/dashboard");
    } catch {
      removeStoredSession();
    }
  }, []);

  function persistLogin(emailAddress: string, accessToken: string, refreshToken: string) {
    const session = JSON.stringify({
      email: emailAddress,
      accessToken,
      refreshToken,
    });
    localStorage.setItem("clearleaf.auth", session);
    setSignedInEmail(emailAddress);
  }

  function signOut() {
    removeStoredSession();
    setSignedInEmail("");
    setLoginEmail("");
    setLoginPassword("");
    setStatus("Signed out.");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setStatus("");
    if (!email.trim() || !password || !confirmPassword) return setError("Email and password are required.");
    if (password !== confirmPassword) return setError("Passwords do not match.");
    if (password.length < 8) return setError("Password must be at least 8 characters.");
    if (captchaInput.trim().toUpperCase() !== captchaCode) return setError("Captcha code did not match.");
    if (!termsAccepted) return setError(`You must accept the ${applicationName} terms.`);
    setSubmitting(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/public/signup-requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim(), displayName: name.trim(), password, legalVersion, termsAccepted }),
      });
      const nextMessage = await message(response);
      if (!response.ok) throw new Error(nextMessage);
      setStatus(nextMessage);
      setName("");
      setEmail("");
      setPassword("");
      setConfirmPassword("");
      setCaptchaInput("");
      setCaptchaCode(captcha());
      setTermsAccepted(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to submit signup request.");
    } finally {
      setSubmitting(false);
    }
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setStatus("");
    if (!loginEmail.trim() || !loginPassword) return setError("Email and password are required.");
    setSubmitting(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/public/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: loginEmail.trim(), password: loginPassword }),
      });
      const body = await response.json().catch(() => ({}));
      const nextMessage = body.message || body.error || `Request failed with ${response.status}`;
      if (!response.ok) throw new Error(nextMessage);
      persistLogin(body.email ?? loginEmail.trim(), body.accessToken ?? "", body.refreshToken ?? "");
      setStatus(nextMessage);
      setLoginPassword("");
      window.location.replace("/dashboard");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to sign in.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="account-panel">
      {showBackLink ? <a className="back-link" href="/">← Back to {applicationName}</a> : null}
      <div className="eyebrow">{applicationName} Account</div>
      <h1>{mode === "login" ? "Welcome back" : "Create an account"}</h1>
      <p className="lede">
        {mode === "login"
          ? "Sign in with your email and password."
          : approvalRequired
            ? "Your request will be emailed to the configured approver before your account is created."
            : "Approval is disabled. Your account will be created immediately."}
      </p>
      <div className="account-tabs">
        <button type="button" className={mode === "login" ? "tab active" : "tab"} onClick={() => setMode("login")}>Log in</button>
        <button type="button" className={mode === "signup" ? "tab active" : "tab"} onClick={() => setMode("signup")}>Sign up</button>
      </div>
      {signedInEmail ? <p className="notice success">Signed in as {signedInEmail}.</p> : null}
      {status ? <p className="notice success">{status}</p> : null}
      {error ? <p className="notice error">{error}</p> : null}
      {mode === "login" ? (
        <form className="account-form" onSubmit={login}>
          <label>Email<input type="email" value={loginEmail} onChange={(event) => setLoginEmail(event.target.value)} autoComplete="email" /></label>
          <label>Password<input type="password" value={loginPassword} onChange={(event) => setLoginPassword(event.target.value)} autoComplete="current-password" /></label>
          <button className="primary-button" disabled={submitting}>{submitting ? "Signing in..." : "Sign in"}</button>
          {signedInEmail ? <button type="button" className="secondary-button" onClick={signOut}>Sign out</button> : null}
        </form>
      ) : (
        <form className="account-form" onSubmit={submit}>
          <label>Name<input value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" /></label>
          <label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" /></label>
          <div className="form-grid">
            <label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" /></label>
            <label>Confirm password<input type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" /></label>
          </div>
          <div className="captcha"><strong>{captchaCode}</strong><button type="button" onClick={() => setCaptchaCode(captcha())}>Refresh</button></div>
          <label>Enter captcha<input value={captchaInput} onChange={(event) => setCaptchaInput(event.target.value)} autoComplete="off" /></label>
          <label className="check"><input type="checkbox" checked={termsAccepted} onChange={(event) => setTermsAccepted(event.target.checked)} />I accept the <a href="/terms">{applicationName} terms</a>.</label>
          <button className="primary-button" disabled={submitting}>{submitting ? "Submitting..." : approvalRequired ? "Request approval" : "Create account"}</button>
        </form>
      )}
    </section>
  );
}
