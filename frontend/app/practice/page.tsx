"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

type Session = {
  email?: string;
  accessToken?: string;
};

type ProfilePayload = {
  email?: string;
  name?: string;
  preferred_username?: string;
  username?: string;
  roles?: string[];
  [key: string]: unknown;
};

type StudentTaxonomyNode = {
  id: string;
  parentId?: string | null;
  externalKey?: string | null;
  nodeKey: string;
  displayName: string;
  levelKey: string;
  gradeLabel: string;
  path: string;
  questionCount: number;
};

type Difficulty = "EASY" | "MEDIUM" | "HARD" | "MIXED";

const difficultyOptions: Array<{ value: Difficulty; label: string; time: string }> = [
  { value: "EASY", label: "Easy", time: "60 sec/question" },
  { value: "MEDIUM", label: "Medium", time: "45 sec/question" },
  { value: "HARD", label: "Hard", time: "30 sec/question" },
  { value: "MIXED", label: "Mixed", time: "Even spread" },
];

function readStoredSession() {
  return localStorage.getItem("clearleaf.auth");
}

function removeStoredSession() {
  localStorage.removeItem("clearleaf.auth");
}

function decodePayload(token: string): ProfilePayload | null {
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

function textClaim(payload: ProfilePayload | null, keys: string[]) {
  if (!payload) return "";
  for (const key of keys) {
    const value = payload[key];
    if (typeof value === "string" && value.trim()) return value.trim();
    if (typeof value === "number") return String(value);
  }
  return "";
}

function normalizeGrade(value: string) {
  const normalized = value.trim().toLowerCase();
  const number = normalized.match(/\d+/)?.[0];
  return number ? `grade ${number}` : normalized;
}

function displayName(payload: ProfilePayload | null, session: Session | null) {
  return payload?.name ?? payload?.preferred_username ?? payload?.username ?? session?.email ?? "Student";
}

export default function PracticePage() {
  const router = useRouter();
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";
  const [session, setSession] = useState<Session | null>(null);
  const [profile, setProfile] = useState<ProfilePayload | null>(null);
  const [taxonomies, setTaxonomies] = useState<StudentTaxonomyNode[]>([]);
  const [selectedTaxonomy, setSelectedTaxonomy] = useState<StudentTaxonomyNode | null>(null);
  const [query, setQuery] = useState("");
  const [difficulty, setDifficulty] = useState<Difficulty>("MEDIUM");
  const [questionCount, setQuestionCount] = useState(10);
  const [testName, setTestName] = useState("");
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [starting, setStarting] = useState(false);

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
      setProfile(decodePayload(parsed.accessToken));
      fetch(`${apiBaseUrl}/api/me`, {
        headers: { Authorization: `Bearer ${parsed.accessToken}` },
      })
        .then(async (response) => {
          const body = await response.json().catch(() => ({}));
          if (!response.ok) throw new Error(body.error || `Request failed with ${response.status}`);
          setProfile(body);
        })
        .catch(() => {
          removeStoredSession();
          router.replace("/account");
        });
    } catch {
      removeStoredSession();
      router.replace("/account");
    }
  }, [apiBaseUrl, router]);

  useEffect(() => {
    if (!session?.accessToken) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(async () => {
      setLoading(true);
      setError("");
      try {
        const response = await fetch(`${apiBaseUrl}/api/student/taxonomy/search?query=${encodeURIComponent(query.trim())}`, {
          headers: { Authorization: `Bearer ${session.accessToken}` },
          signal: controller.signal,
        });
        const body = await response.json().catch(() => []);
        if (!response.ok) throw new Error(body.error || `Request failed with ${response.status}`);
        setTaxonomies(body);
      } catch (exception) {
        if (exception instanceof DOMException && exception.name === "AbortError") return;
        setError(exception instanceof Error ? exception.message : "Unable to load active taxonomies.");
      } finally {
        setLoading(false);
      }
    }, 250);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [apiBaseUrl, query, session?.accessToken]);

  const studentGrade = useMemo(() => {
    const claim = textClaim(profile, ["grade", "student_grade", "gradeLevel", "grade_level", "class", "classLevel"]);
    return claim ? normalizeGrade(claim) : "";
  }, [profile]);

  const visibleTaxonomies = useMemo(() => {
    if (query.trim() || !studentGrade) return taxonomies;
    const matchesGrade = taxonomies.filter((node) => normalizeGrade(node.gradeLabel) === studentGrade);
    return matchesGrade.length ? matchesGrade : taxonomies;
  }, [query, studentGrade, taxonomies]);

  const groupedTaxonomies = useMemo(() => {
    const groups = new Map<string, StudentTaxonomyNode[]>();
    for (const node of visibleTaxonomies) {
      const grade = node.gradeLabel || "Ungraded";
      groups.set(grade, [...(groups.get(grade) ?? []), node]);
    }
    return [...groups.entries()].map(([grade, nodes]) => ({
      grade,
      nodes: nodes
        .filter((node) => ["SUBJECT", "CHAPTER", "TOPIC", "GRADE"].includes(node.levelKey))
        .sort((first, second) => first.path.localeCompare(second.path)),
    })).filter((group) => group.nodes.length > 0);
  }, [visibleTaxonomies]);

  async function startTest() {
    if (!selectedTaxonomy) {
      setError("Select a subject or topic before starting a test.");
      return;
    }
    setError("");
    setStatus("");
    setStarting(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/student/tests`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session?.accessToken ?? ""}`,
        },
        body: JSON.stringify({
          taxonomyNodeId: selectedTaxonomy.id,
          difficulty,
          questionCount,
          testName: testName.trim() || `${selectedTaxonomy.displayName} Test`,
        }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || body.message || `Request failed with ${response.status}`);
      router.push(`/practice/tests/${body.attemptId}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to start test.");
    } finally {
      setStarting(false);
    }
  }

  return (
    <main className="student-shell">
      <section className="student-panel">
        <a className="secondary-button compact-button page-nav-button" href="/dashboard">Dashboard</a>
        <div className="student-header">
          <div>
            <div className="eyebrow">Student test center</div>
            <h1>Find a test</h1>
            <p className="lede">Choose an active subject or topic, then set the test difficulty and length.</p>
          </div>
          <div className="student-profile-card">
            <strong>{displayName(profile, session)}</strong>
            <span>{studentGrade ? `Default grade: ${studentGrade.replace(/\b\w/g, (letter) => letter.toUpperCase())}` : "No grade found in profile"}</span>
          </div>
        </div>

        <div className="student-layout">
          <section className="student-search-panel">
            <label className="student-search">
              Search active taxonomies
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search math, English, fractions..."
              />
            </label>
            <div className="student-result-meta">
              <span>{loading ? "Loading..." : `${visibleTaxonomies.length} active result(s)`}</span>
              {query.trim() ? <button type="button" className="secondary-button compact-button" onClick={() => setQuery("")}>Clear</button> : null}
            </div>

            <div className="student-results">
              {groupedTaxonomies.map((group) => (
                <section className="student-grade-group" key={group.grade}>
                  <h2>{group.grade}</h2>
                  <div className="student-taxonomy-list">
                    {group.nodes.map((node) => (
                      <button
                        type="button"
                        key={node.id}
                        className={selectedTaxonomy?.id === node.id ? "student-taxonomy-card active" : "student-taxonomy-card"}
                        onClick={() => {
                          setSelectedTaxonomy(node);
                          setStatus("");
                          setError("");
                          if (!testName.trim()) setTestName(`${node.displayName} ${difficultyOptions.find((option) => option.value === difficulty)?.label ?? "Practice"} Test`);
                        }}
                      >
                        <span className="student-taxonomy-title">
                          <strong>{node.displayName}</strong>
                          <small>{node.levelKey}</small>
                        </span>
                        <span>{node.path}</span>
                        <span>{node.questionCount} available question(s)</span>
                      </button>
                    ))}
                  </div>
                </section>
              ))}
              {!loading && groupedTaxonomies.length === 0 ? <p className="notice warning">No active subjects or topics matched.</p> : null}
            </div>
          </section>

          <aside className="test-setup-panel">
            <h2>Test setup</h2>
            <div className="selected-topic-card">
              <strong>{selectedTaxonomy?.displayName ?? "No topic selected"}</strong>
              <span>{selectedTaxonomy?.path ?? "Choose a subject or topic from the active taxonomy results."}</span>
            </div>
            <label>
              Test name
              <input value={testName} onChange={(event) => setTestName(event.target.value)} placeholder="Practice Test" />
            </label>
            <label>
              Questions
              <input
                type="number"
                min="1"
                max="50"
                value={questionCount}
                onChange={(event) => setQuestionCount(Math.max(1, Math.min(50, Number(event.target.value) || 1)))}
              />
            </label>
            <div className="difficulty-grid">
              {difficultyOptions.map((option) => (
                <button
                  type="button"
                  key={option.value}
                  className={difficulty === option.value ? "difficulty-card active" : "difficulty-card"}
                  onClick={() => setDifficulty(option.value)}
                >
                  <strong>{option.label}</strong>
                  <span>{option.time}</span>
                </button>
              ))}
            </div>
            <button type="button" className="primary-button" disabled={starting} onClick={startTest}>
              {starting ? "Starting..." : "Start test"}
            </button>
          </aside>
        </div>

        {status ? <p className="notice success">{status}</p> : null}
        {error ? <p className="notice error">{error}</p> : null}
      </section>
    </main>
  );
}
