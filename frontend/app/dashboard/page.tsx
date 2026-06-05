"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useApplicationConfig } from "../useApplicationConfig";

type Session = {
  email?: string;
  accessToken?: string;
};

type ProfilePayload = {
  preferred_username?: string;
  email?: string;
  name?: string;
  roles?: string[];
};

type TestAttemptSummary = {
  attemptId: string;
  testName: string;
  taxonomyNodeId: string;
  taxonomyName: string;
  taxonomyPath: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  status: string;
  startedAt: string;
  submittedAt?: string | null;
  scorePoints?: number | null;
  maxPoints: number;
  questionCount: number;
};

type StudentAssignedTestSummary = {
  assignmentId: string;
  attemptId?: string | null;
  testName: string;
  status: string;
  questionCount: number;
  timeAllowedSeconds: number;
  availableFrom?: string | null;
  availableUntil?: string | null;
  assignedAt: string;
  startedAt?: string | null;
  submittedAt?: string | null;
  scorePoints?: number | null;
  maxPoints: number;
  resultsPublished: boolean;
};

type PageResponse<T> = {
  content: T[];
  number: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
};

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

function displayName(payload: ProfilePayload | null, session: Session | null) {
  return payload?.name ?? payload?.preferred_username ?? session?.email ?? "Student";
}

function shortName(payload: ProfilePayload | null, session: Session | null) {
  const value = displayName(payload, session);
  const namePart = value.includes("@") ? value.split("@")[0] : value;
  return namePart.trim().split(/\s+/)[0] || "Student";
}

function formatDate(value?: string | null) {
  if (!value) return "Not submitted";
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export default function DashboardPage() {
  const router = useRouter();
  const { applicationName } = useApplicationConfig();
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";
  const [session, setSession] = useState<Session | null>(null);
  const [profile, setProfile] = useState<ProfilePayload | null>(null);
  const [history, setHistory] = useState<TestAttemptSummary[]>([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyTotalElements, setHistoryTotalElements] = useState(0);
  const [taxonomyFilter, setTaxonomyFilter] = useState("");
  const [dateFromFilter, setDateFromFilter] = useState("");
  const [dateToFilter, setDateToFilter] = useState("");
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [startingAttemptId, setStartingAttemptId] = useState("");
  const [assignedTests, setAssignedTests] = useState<StudentAssignedTestSummary[]>([]);
  const [assignedResults, setAssignedResults] = useState<StudentAssignedTestSummary[]>([]);
  const [dashboardTab, setDashboardTab] = useState<"history" | "take" | "assigned" | "configure">("history");
  const [assignedTab, setAssignedTab] = useState<"assigned" | "results">("assigned");
  const [startingAssignmentId, setStartingAssignmentId] = useState("");
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
      setProfile(decodePayload(parsed.accessToken));
      loadProfile(parsed.accessToken);
    } catch {
      removeStoredSession();
      router.replace("/account");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiBaseUrl, router]);

  useEffect(() => {
    if (!session?.accessToken) return;
    loadHistory(session.accessToken);
    loadAssignedTests(session.accessToken);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken, historyPage, taxonomyFilter, dateFromFilter, dateToFilter]);

  async function request(path: string, token: string, init?: RequestInit) {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        ...(init?.headers ?? {}),
      },
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || body.message || `Request failed with ${response.status}`);
    return body;
  }

  async function loadProfile(token: string) {
    try {
      const body = await request("/api/me", token);
      setProfile(body);
    } catch (exception) {
      removeStoredSession();
      setError(exception instanceof Error ? exception.message : "Session expired.");
      router.replace("/account");
    }
  }

  async function loadHistory(token: string) {
    setLoadingHistory(true);
    setError("");
    try {
      const params = new URLSearchParams({
        page: String(historyPage),
        size: "10",
      });
      if (taxonomyFilter.trim()) params.set("taxonomy", taxonomyFilter.trim());
      if (dateFromFilter) params.set("dateFrom", dateFromFilter);
      if (dateToFilter) params.set("dateTo", dateToFilter);
      const body = await request(`/api/student/tests/history?${params.toString()}`, token) as PageResponse<TestAttemptSummary>;
      setHistory(Array.isArray(body.content) ? body.content : []);
      setHistoryTotalPages(body.totalPages ?? 0);
      setHistoryTotalElements(body.totalElements ?? body.content?.length ?? 0);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load test history.");
    } finally {
      setLoadingHistory(false);
    }
  }

  async function loadAssignedTests(token: string) {
    try {
      const [assignedBody, resultsBody] = await Promise.all([
        request("/api/student/assigned-tests", token),
        request("/api/student/assigned-tests/results", token),
      ]);
      setAssignedTests(Array.isArray(assignedBody) ? assignedBody : []);
      setAssignedResults(Array.isArray(resultsBody) ? resultsBody : []);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load assigned tests.");
    }
  }

  async function startAssignedTest(assignmentId: string) {
    if (!session?.accessToken) return;
    setStartingAssignmentId(assignmentId);
    setError("");
    try {
      await request(`/api/student/assigned-tests/${assignmentId}/start`, session.accessToken, { method: "POST" });
      router.push(`/assigned-tests/${assignmentId}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to start assigned test.");
      setStartingAssignmentId("");
    }
  }

  async function reattempt(attempt: TestAttemptSummary) {
    if (!session?.accessToken) return;
    setStartingAttemptId(attempt.attemptId);
    setError("");
    try {
      const body = await request("/api/student/tests", session.accessToken, {
        method: "POST",
        body: JSON.stringify({
          taxonomyNodeId: attempt.taxonomyNodeId,
          difficulty: attempt.difficulty,
          questionCount: attempt.questionCount,
          testName: `${attempt.taxonomyName} ${attempt.difficulty.toLowerCase()} re-attempt`,
        }),
      });
      router.push(`/practice/tests/${body.attemptId}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to start re-attempt.");
      setStartingAttemptId("");
    }
  }

  function signOut() {
    removeStoredSession();
    router.replace("/account");
  }

  const roles = profile?.roles ?? [];
  const isAdmin = roles.includes("administrator");
  const studentName = shortName(profile, session);

  function resetHistoryPage() {
    if (historyPage !== 0) setHistoryPage(0);
  }

  return (
    <main className="student-shell">
      <section className="student-panel dashboard-panel">
        <div className="dashboard-topbar">
          <details className="profile-menu">
            <summary>{studentName}</summary>
            <div className="profile-menu-items">
              <a href="/account">Edit profile</a>
              <button type="button" onClick={signOut}>Sign out</button>
            </div>
          </details>
        </div>

        <div className="student-header">
          <div className="dashboard-welcome">
            <div className="eyebrow">Student dashboard</div>
            <h1>Welcome to {applicationName}, {studentName}</h1>
          </div>
        </div>

        {error ? <p className="notice error">{error}</p> : null}

        <div className="account-tabs dashboard-tabs" role="tablist" aria-label="Dashboard sections">
          <button type="button" role="tab" aria-selected={dashboardTab === "history"} className={dashboardTab === "history" ? "tab active" : "tab"} onClick={() => setDashboardTab("history")}>Historical Tests</button>
          <button type="button" role="tab" aria-selected={dashboardTab === "take"} className={dashboardTab === "take" ? "tab active" : "tab"} onClick={() => setDashboardTab("take")}>Take Test</button>
          <button type="button" role="tab" aria-selected={dashboardTab === "assigned"} className={dashboardTab === "assigned" ? "tab active" : "tab"} onClick={() => setDashboardTab("assigned")}>Assigned Tests</button>
          {isAdmin ? (
            <button type="button" role="tab" aria-selected={dashboardTab === "configure"} className={dashboardTab === "configure" ? "tab active" : "tab"} onClick={() => setDashboardTab("configure")}>Configure</button>
          ) : null}
        </div>

        {isAdmin && dashboardTab === "configure" ? (
          <section className="dashboard-admin-panel">
            <div>
              <h2>Configure Taxonomy and Questions</h2>
              <p>Manage taxonomy nodes, question imports, manual authoring, and review workflows.</p>
            </div>
            <a className="primary-button" href="/admin">Configure</a>
          </section>
        ) : null}

        {dashboardTab === "take" ? (
        <section className="dashboard-test-hero">
          <div>
            <h2>Take Test</h2>
            <p>Browse subjects and topics with available questions, then choose difficulty and length.</p>
          </div>
          <a className="primary-button" href="/practice">Take test</a>
        </section>
        ) : null}

        {dashboardTab === "assigned" ? (
        <section className="dashboard-history">
          <div className="section-header">
            <h2>Assigned Tests</h2>
            <p>{assignedTests.length} active assignment(s)</p>
          </div>
          <div className="account-tabs import-tabs" role="tablist" aria-label="Assigned test tabs">
            <button type="button" role="tab" aria-selected={assignedTab === "assigned"} className={assignedTab === "assigned" ? "tab active" : "tab"} onClick={() => setAssignedTab("assigned")}>Assigned</button>
            <button type="button" role="tab" aria-selected={assignedTab === "results"} className={assignedTab === "results" ? "tab active" : "tab"} onClick={() => setAssignedTab("results")}>Results</button>
          </div>
          <div className="history-list">
            {(assignedTab === "assigned" ? assignedTests : assignedResults).map((assignment) => (
              <article className="history-row" key={assignment.assignmentId}>
                <div>
                  <strong>{assignment.testName}</strong>
                  <span>{assignment.questionCount} question(s), {Math.ceil(assignment.timeAllowedSeconds / 60)} minute(s)</span>
                </div>
                <div className="history-meta">
                  <span>{assignment.status}</span>
                  <span>Assigned: {formatDate(assignment.assignedAt)}</span>
                  {assignment.submittedAt ? <span>Submitted: {formatDate(assignment.submittedAt)}</span> : null}
                </div>
                <div className="history-score">
                  {assignedTab === "results" ? (
                    <>
                      <strong>{assignment.scorePoints ?? 0} / {assignment.maxPoints}</strong>
                      <span>Published result</span>
                    </>
                  ) : (
                    <>
                      <strong>{assignment.startedAt ? "In progress" : "Not started"}</strong>
                      <span>{assignment.availableUntil ? `Due ${formatDate(assignment.availableUntil)}` : "No due date"}</span>
                    </>
                  )}
                </div>
                <div className="history-actions">
                  {assignedTab === "assigned" ? (
                    <button
                      type="button"
                      className="primary-button"
                      disabled={startingAssignmentId === assignment.assignmentId}
                      onClick={() => startAssignedTest(assignment.assignmentId)}
                    >
                      {startingAssignmentId === assignment.assignmentId ? "Starting..." : (assignment.startedAt ? "Continue" : "Start")}
                    </button>
                  ) : (
                    <a className="secondary-button" href={`/assigned-tests/${assignment.assignmentId}`}>Details</a>
                  )}
                </div>
              </article>
            ))}
            {assignedTab === "assigned" && assignedTests.length === 0 ? <p className="notice warning">No assigned tests available.</p> : null}
            {assignedTab === "results" && assignedResults.length === 0 ? <p className="notice warning">No published results available.</p> : null}
          </div>
        </section>
        ) : null}

        {dashboardTab === "history" ? (
        <section className="dashboard-history">
          <div className="section-header">
            <h2>Historical Tests</h2>
            <p>{loadingHistory ? "Loading attempts..." : `${historyTotalElements} attempt(s)`}</p>
          </div>

          <div className="history-filters">
            <label>
              Date from
              <input type="date" value={dateFromFilter} onChange={(event) => {
                setDateFromFilter(event.target.value);
                resetHistoryPage();
              }} />
            </label>
            <label>
              Date to
              <input type="date" value={dateToFilter} onChange={(event) => {
                setDateToFilter(event.target.value);
                resetHistoryPage();
              }} />
            </label>
            <label>
              Taxonomy
              <input value={taxonomyFilter} onChange={(event) => {
                setTaxonomyFilter(event.target.value);
                resetHistoryPage();
              }} placeholder="General Maths" />
            </label>
            <button type="button" className="secondary-button" onClick={() => {
              setDateFromFilter("");
              setDateToFilter("");
              setTaxonomyFilter("");
              setHistoryPage(0);
            }}>Clear</button>
          </div>

          <div className="history-list">
            {history.map((attempt) => (
              <article className="history-row" key={attempt.attemptId}>
                <div>
                  <strong>{attempt.testName}</strong>
                  <span>{attempt.taxonomyPath}</span>
                </div>
                <div className="history-meta">
                  <span>{attempt.difficulty}</span>
                  <span>{attempt.status}</span>
                  <span>Date/time: {formatDate(attempt.submittedAt ?? attempt.startedAt)}</span>
                </div>
                <div className="history-score">
                  <strong>{attempt.scorePoints ?? 0} / {attempt.maxPoints}</strong>
                  <span>{attempt.questionCount} question(s)</span>
                </div>
                <div className="history-actions">
                  <a className="secondary-button" href={`/practice/tests/${attempt.attemptId}`}>Details</a>
                  <button
                    type="button"
                    className="primary-button"
                    disabled={startingAttemptId === attempt.attemptId}
                    onClick={() => reattempt(attempt)}
                  >
                    {startingAttemptId === attempt.attemptId ? "Starting..." : "Re-attempt"}
                  </button>
                </div>
              </article>
            ))}
            {!loadingHistory && history.length === 0 ? <p className="notice warning">No tests attempted yet.</p> : null}
          </div>

          <div className="pagination-bar history-pagination">
            <span>Page {historyTotalPages === 0 ? 0 : historyPage + 1} of {historyTotalPages}</span>
            <div className="pagination-actions">
              <button type="button" className="secondary-button compact-button" disabled={historyPage <= 0 || loadingHistory} onClick={() => setHistoryPage((value) => Math.max(0, value - 1))}>Previous</button>
              <button type="button" className="secondary-button compact-button" disabled={historyPage >= historyTotalPages - 1 || loadingHistory} onClick={() => setHistoryPage((value) => value + 1)}>Next</button>
            </div>
          </div>
        </section>
        ) : null}
      </section>
    </main>
  );
}
