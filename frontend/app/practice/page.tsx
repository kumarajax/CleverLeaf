"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

type Session = {
  email?: string;
  accessToken?: string;
};

type PracticeOption = {
  key: string;
  text: string;
};

type SampleQuestion = {
  id: string;
  title: string;
  prompt: string;
  options: PracticeOption[];
  correctKeys: string[];
};

const sampleQuestion: SampleQuestion = {
  id: "fraction-addition-001",
  title: "Grade 5 Math",
  prompt: "What is 2/3 + 1/2?",
  options: [
    { key: "A", text: "7/6" },
    { key: "B", text: "5/6" },
    { key: "C", text: "1/6" },
    { key: "D", text: "2/5" },
  ],
  correctKeys: ["A"],
};

function readStoredSession() {
  return localStorage.getItem("clearleaf.auth");
}

function removeStoredSession() {
  localStorage.removeItem("clearleaf.auth");
}

export default function PracticePage() {
  const router = useRouter();
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";
  const [session, setSession] = useState<Session | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [result, setResult] = useState("");
  const [error, setError] = useState("");

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
    } catch {
      removeStoredSession();
      router.replace("/account");
    }
  }, [router]);

  const token = session?.accessToken ?? "";
  const selectedLabel = useMemo(() => {
    if (!selectedKeys.length) return "No answer selected";
    return selectedKeys.join(", ");
  }, [selectedKeys]);

  async function submitAnswer() {
    setError("");
    setResult("");
    try {
      const response = await fetch(`${apiBaseUrl}/api/questions/score`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          question: {
            type: "SINGLE_SELECT",
            difficulty: "MEDIUM",
            workflowStatus: "READY_FOR_TEST",
            questionText: sampleQuestion.prompt,
            explanation: "Use common denominator 6: 2/3 = 4/6 and 1/2 = 3/6, so the answer is 7/6.",
            sourceReference: "ClearLeaf sample",
            licenseCategory: "CC-BY",
            options: sampleQuestion.options.map((option) => ({
              key: option.key,
              text: option.text,
              correct: sampleQuestion.correctKeys.includes(option.key),
            })),
          },
          submittedOptionKeys: selectedKeys,
        }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || `Request failed with ${response.status}`);
      }
      setResult(body.correct ? "Correct answer." : "Incorrect answer.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to score answer.");
    }
  }

  function toggleOption(key: string) {
    setSelectedKeys([key]);
  }

  return (
    <main className="account-shell">
      <section className="account-panel practice-panel">
        <a className="back-link" href="/dashboard">← Back to dashboard</a>
        <div className="eyebrow">Practice mode</div>
        <h1>{sampleQuestion.title}</h1>
        <p className="lede">Choose an answer and score it against the ClearLeaf scorer.</p>

        <div className="practice-card">
          <strong>{sampleQuestion.prompt}</strong>
          <div className="practice-options">
            {sampleQuestion.options.map((option) => (
              <button
                key={option.key}
                type="button"
                className={selectedKeys.includes(option.key) ? "practice-option active" : "practice-option"}
                onClick={() => toggleOption(option.key)}
              >
                <span>{option.key}</span>
                <span>{option.text}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="dashboard-actions">
          <button type="button" className="primary-button" onClick={submitAnswer}>Submit answer</button>
          <a className="secondary-button" href="/dashboard">Back to dashboard</a>
        </div>

        <div className="session-card">
          <div><strong>Selected</strong><span>{selectedLabel}</span></div>
          <div><strong>User</strong><span>{session?.email ?? "Signed in user"}</span></div>
        </div>

        {result ? <p className="notice success">{result}</p> : null}
        {error ? <p className="notice error">{error}</p> : null}
      </section>
    </main>
  );
}
