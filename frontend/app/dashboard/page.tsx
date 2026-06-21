"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AdminConsole } from "../admin/page";
import { PracticeCenter } from "../practice/page";
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
  tenantMemberships?: TenantMembership[];
};

type TenantMembership = {
  tenantId: string;
  tenantName: string;
  role: string;
  status: string;
};

type TestAttemptSummary = {
  attemptId: string;
  testName: string;
  taxonomyNodeId: string;
  taxonomyName: string;
  taxonomyPath: string;
  difficulty: "EASY" | "MEDIUM" | "HARD" | "MIXED";
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

type TenantSecurityMembership = {
  membershipId: string;
  tenantId: string;
  email: string;
  role: "ADMIN" | "STUDENT";
  status: string;
  createdAt: string;
  updatedAt: string;
};

type TenantInvitation = {
  invitationId: string;
  tenantId: string;
  email: string;
  role: "ADMIN" | "STUDENT";
  status: string;
  expiresAt: string;
};

type UserTenantInvitation = TenantInvitation & {
  tenantName: string;
  createdAt: string;
};

const defaultTenantId = "00000000-0000-0000-0000-000000000100";

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

function NotificationsPanel({
  invitations,
  loading,
  actionInvitationId,
  onAccept,
  onReject,
}: {
  invitations: UserTenantInvitation[];
  loading: boolean;
  actionInvitationId: string;
  onAccept: (invitation: UserTenantInvitation) => void;
  onReject: (invitation: UserTenantInvitation) => void;
}) {
  return (
    <section className="dashboard-history notifications-panel">
      <div className="section-header">
        <h2>Notifications</h2>
        <p>{loading ? "Loading invitations..." : `${invitations.length} pending invitation(s)`}</p>
      </div>
      <div className="history-list">
        {invitations.map((invitation) => (
          <article className="history-row notification-row" key={invitation.invitationId}>
            <div>
              <strong>{invitation.tenantName}</strong>
              <span>{invitation.email}</span>
            </div>
            <div className="history-meta">
              <span>Tenant invitation</span>
              <span>Role: {invitation.role}</span>
              <span>Expires: {formatDate(invitation.expiresAt)}</span>
            </div>
            <div className="history-score">
              <strong>{invitation.status}</strong>
              <span>Created {formatDate(invitation.createdAt)}</span>
            </div>
            <div className="history-actions">
              <button
                type="button"
                className="primary-button compact-button"
                disabled={actionInvitationId === invitation.invitationId}
                onClick={() => onAccept(invitation)}
              >
                {actionInvitationId === invitation.invitationId ? "Working..." : "Accept"}
              </button>
              <button
                type="button"
                className="secondary-button compact-button"
                disabled={actionInvitationId === invitation.invitationId}
                onClick={() => onReject(invitation)}
              >
                Reject
              </button>
            </div>
          </article>
        ))}
        {!loading && invitations.length === 0 ? <p className="notice warning">No pending invitations.</p> : null}
      </div>
    </section>
  );
}

function TenantSecurityPanel({
  apiBaseUrl,
  token,
  tenantId,
  tenantName,
}: {
  apiBaseUrl: string;
  token: string;
  tenantId: string;
  tenantName: string;
}) {
  const [activeTab, setActiveTab] = useState<"invite" | "roles">("invite");
  const [inviteEmail, setInviteEmail] = useState("");
  const [memberships, setMemberships] = useState<TenantSecurityMembership[]>([]);
  const [invitations, setInvitations] = useState<TenantInvitation[]>([]);
  const [loading, setLoading] = useState(false);
  const [submittingInvite, setSubmittingInvite] = useState(false);
  const [updatingMembershipId, setUpdatingMembershipId] = useState("");
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token || !tenantId) return;
    loadSecurityData().catch((exception) => {
      setError(exception instanceof Error ? exception.message : "Unable to load tenant security.");
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, tenantId]);

  async function tenantRequest(path: string, init?: RequestInit) {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "X-CleverLeaf-Tenant-Id": tenantId,
        ...(init?.headers ?? {}),
      },
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || body.message || `Request failed with ${response.status}`);
    return body;
  }

  async function loadSecurityData() {
    setLoading(true);
    setError("");
    try {
      const [membershipBody, invitationBody] = await Promise.all([
        tenantRequest("/api/admin/tenant/memberships"),
        tenantRequest("/api/admin/tenant/invitations"),
      ]);
      setMemberships(Array.isArray(membershipBody) ? membershipBody : []);
      setInvitations(Array.isArray(invitationBody) ? invitationBody : []);
    } finally {
      setLoading(false);
    }
  }

  async function inviteStudent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const email = inviteEmail.trim();
    if (!email) return setError("Email is required.");
    setSubmittingInvite(true);
    setError("");
    setStatus("");
    try {
      await tenantRequest("/api/admin/tenant/invitations", {
        method: "POST",
        body: JSON.stringify({ email, role: "STUDENT" }),
      });
      setInviteEmail("");
      setStatus(`Invitation sent to ${email}.`);
      await loadSecurityData();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to invite user.");
    } finally {
      setSubmittingInvite(false);
    }
  }

  async function updateRole(membership: TenantSecurityMembership, role: "ADMIN" | "STUDENT") {
    if (membership.role === role) return;
    setUpdatingMembershipId(membership.membershipId);
    setError("");
    setStatus("");
    try {
      const updated = await tenantRequest(`/api/admin/tenant/memberships/${membership.membershipId}/role`, {
        method: "PUT",
        body: JSON.stringify({ role }),
      }) as TenantSecurityMembership;
      setMemberships((current) => current.map((item) => item.membershipId === updated.membershipId ? updated : item));
      setStatus(`${membership.email} is now ${role.toLowerCase()}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to update role.");
    } finally {
      setUpdatingMembershipId("");
    }
  }

  const pendingInvitations = invitations.filter((invitation) => invitation.status === "PENDING");

  return (
    <section className="dashboard-history tenant-security-panel">
      <div className="section-header">
        <h2>Tenant Security</h2>
        <p>{tenantName} tenant</p>
      </div>
      {error ? <p className="notice error">{error}</p> : null}
      {status ? <p className="notice success">{status}</p> : null}
      <div className="account-tabs import-tabs tenant-security-tabs" role="tablist" aria-label="Tenant security tabs">
        <button type="button" role="tab" aria-selected={activeTab === "invite"} className={activeTab === "invite" ? "tab active" : "tab"} onClick={() => setActiveTab("invite")}>Invite Users</button>
        <button type="button" role="tab" aria-selected={activeTab === "roles"} className={activeTab === "roles" ? "tab active" : "tab"} onClick={() => setActiveTab("roles")}>Roles</button>
      </div>

      {activeTab === "invite" ? (
        <div className="card table-card tenant-security-card">
          <div className="section-header compact-section-header">
            <h3>Invite student</h3>
            <p>{pendingInvitations.length} pending invitation(s)</p>
          </div>
          <form className="account-form tenant-invite-form" onSubmit={inviteStudent}>
            <label>Email<input type="email" value={inviteEmail} onChange={(event) => setInviteEmail(event.target.value)} placeholder="student@example.com" /></label>
            <button className="primary-button compact-button" disabled={submittingInvite}>{submittingInvite ? "Sending..." : "Invite Student"}</button>
          </form>
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Email</th><th>Role</th><th>Status</th><th>Expires</th></tr></thead>
              <tbody>
                {invitations.map((invitation) => (
                  <tr key={invitation.invitationId}>
                    <td>{invitation.email}</td>
                    <td>{invitation.role}</td>
                    <td>{invitation.status}</td>
                    <td>{formatDate(invitation.expiresAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!loading && invitations.length === 0 ? <p className="notice warning">No invitations found.</p> : null}
          </div>
        </div>
      ) : null}

      {activeTab === "roles" ? (
        <div className="card table-card tenant-security-card">
          <div className="section-header compact-section-header">
            <h3>Tenant roles</h3>
            <p>{loading ? "Loading members..." : `${memberships.length} active member(s)`}</p>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Email</th><th>Role</th><th>Status</th><th>Updated</th><th>Assign role</th></tr></thead>
              <tbody>
                {memberships.map((membership) => (
                  <tr key={membership.membershipId}>
                    <td>{membership.email}</td>
                    <td>{membership.role}</td>
                    <td>{membership.status}</td>
                    <td>{formatDate(membership.updatedAt)}</td>
                    <td>
                      {membership.role === "ADMIN" ? (
                        <span className="role-lock">Admin retained</span>
                      ) : (
                        <select
                          value={membership.role}
                          disabled={updatingMembershipId === membership.membershipId}
                          onChange={(event) => updateRole(membership, event.target.value as "ADMIN" | "STUDENT")}
                        >
                          <option value="STUDENT">Student</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!loading && memberships.length === 0 ? <p className="notice warning">No tenant members found.</p> : null}
          </div>
        </div>
      ) : null}
    </section>
  );
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
  const [dashboardTab, setDashboardTab] = useState<"history" | "take" | "assigned" | "configure" | "tenant-security" | "notifications">("history");
  const [assignedTab, setAssignedTab] = useState<"assigned" | "results">("assigned");
  const [startingAssignmentId, setStartingAssignmentId] = useState("");
  const [tenantInvitations, setTenantInvitations] = useState<UserTenantInvitation[]>([]);
  const [loadingInvitations, setLoadingInvitations] = useState(false);
  const [actionInvitationId, setActionInvitationId] = useState("");
  const [selectedTenantId, setSelectedTenantId] = useState("");
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
    loadTenantInvitations(session.accessToken);
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

  async function loadTenantInvitations(token: string) {
    setLoadingInvitations(true);
    try {
      const body = await request("/api/student/tenant-invitations", token);
      setTenantInvitations(Array.isArray(body) ? body : []);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load notifications.");
    } finally {
      setLoadingInvitations(false);
    }
  }

  async function acceptTenantInvitation(invitation: UserTenantInvitation) {
    if (!session?.accessToken) return;
    setActionInvitationId(invitation.invitationId);
    setError("");
    try {
      await request(`/api/student/tenant-invitations/${invitation.invitationId}/accept`, session.accessToken, { method: "POST" });
      setTenantInvitations((current) => current.filter((item) => item.invitationId !== invitation.invitationId));
      await loadProfile(session.accessToken);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to accept invitation.");
    } finally {
      setActionInvitationId("");
    }
  }

  async function rejectTenantInvitation(invitation: UserTenantInvitation) {
    if (!session?.accessToken) return;
    setActionInvitationId(invitation.invitationId);
    setError("");
    try {
      await request(`/api/student/tenant-invitations/${invitation.invitationId}/reject`, session.accessToken, { method: "POST" });
      setTenantInvitations((current) => current.filter((item) => item.invitationId !== invitation.invitationId));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to reject invitation.");
    } finally {
      setActionInvitationId("");
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
  const isDemoAdmin = roles.includes("DEMO_ADMIN");
  const isPlatformAdmin = roles.includes("administrator");
  const adminTenantOptions = [
    ...(isDemoAdmin || isPlatformAdmin ? [{ tenantId: defaultTenantId, tenantName: "DEMO", role: "ADMIN", status: "ACTIVE" }] : []),
    ...((profile?.tenantMemberships ?? []).filter((membership) => membership.role === "ADMIN" && membership.status === "ACTIVE")),
  ].filter((membership, index, all) => all.findIndex((candidate) => candidate.tenantId === membership.tenantId) === index);
  const isAdmin = isPlatformAdmin
    || isDemoAdmin
    || adminTenantOptions.length > 0;
  const effectiveAdminTenantId = selectedTenantId || adminTenantOptions[0]?.tenantId || (isPlatformAdmin ? defaultTenantId : "");
  const effectiveAdminTenantName = adminTenantOptions.find((tenant) => tenant.tenantId === effectiveAdminTenantId)?.tenantName
    ?? (effectiveAdminTenantId === defaultTenantId ? "DEMO" : "");
  const studentName = shortName(profile, session);

  useEffect(() => {
    if (!isAdmin) return;
    if (selectedTenantId && (adminTenantOptions.some((tenant) => tenant.tenantId === selectedTenantId) || isPlatformAdmin)) return;
    const nextTenantId = adminTenantOptions[0]?.tenantId || (isPlatformAdmin ? defaultTenantId : "");
    if (nextTenantId) setSelectedTenantId(nextTenantId);
  }, [adminTenantOptions, isAdmin, isPlatformAdmin, selectedTenantId]);

  function resetHistoryPage() {
    if (historyPage !== 0) setHistoryPage(0);
  }

  return (
    <main className="student-shell">
      <section className={dashboardTab === "configure" || dashboardTab === "tenant-security" ? "student-panel dashboard-panel configure-dashboard-panel" : "student-panel dashboard-panel"}>
        <div className="dashboard-topbar">
          {isAdmin && adminTenantOptions.length > 1 ? (
            <label className="tenant-switcher">
              Tenant
              <select value={effectiveAdminTenantId} onChange={(event) => setSelectedTenantId(event.target.value)}>
                {adminTenantOptions.map((tenant) => (
                  <option key={tenant.tenantId} value={tenant.tenantId}>{tenant.tenantName}</option>
                ))}
              </select>
            </label>
          ) : null}
          <button
            type="button"
            className={dashboardTab === "notifications" ? "notification-button active" : "notification-button"}
            aria-label="Notifications"
            onClick={() => setDashboardTab("notifications")}
          >
            {tenantInvitations.length > 0 ? <span className="notification-badge">{tenantInvitations.length}</span> : null}
          </button>
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
            <h1>Welcome to {applicationName}, {studentName}</h1>
          </div>
        </div>

        {error ? <p className="notice error">{error}</p> : null}

        <div className="dashboard-workspace">
          <aside className="dashboard-sidebar" aria-label="Dashboard navigation">
            <div className="account-tabs dashboard-tabs" role="tablist" aria-label="Dashboard sections">
              <button type="button" role="tab" aria-selected={dashboardTab === "history"} className={dashboardTab === "history" ? "tab active" : "tab"} onClick={() => setDashboardTab("history")}>Historical Tests</button>
              <button type="button" role="tab" aria-selected={dashboardTab === "take"} className={dashboardTab === "take" ? "tab active" : "tab"} onClick={() => setDashboardTab("take")}>Take Test</button>
              <button type="button" role="tab" aria-selected={dashboardTab === "assigned"} className={dashboardTab === "assigned" ? "tab active" : "tab"} onClick={() => setDashboardTab("assigned")}>Assigned Tests</button>
              {isAdmin ? (
                <button type="button" role="tab" aria-selected={dashboardTab === "configure"} className={dashboardTab === "configure" ? "tab active" : "tab"} onClick={() => setDashboardTab("configure")}>Configure</button>
              ) : null}
              {isAdmin ? (
                <button type="button" role="tab" aria-selected={dashboardTab === "tenant-security"} className={dashboardTab === "tenant-security" ? "tab active" : "tab"} onClick={() => setDashboardTab("tenant-security")}>Tenant Security</button>
              ) : null}
            </div>
          </aside>

          <div className="dashboard-main">
            {isAdmin && dashboardTab === "configure" ? (
              <AdminConsole embedded tenantId={effectiveAdminTenantId} />
            ) : null}

            {isAdmin && dashboardTab === "tenant-security" && session?.accessToken && effectiveAdminTenantId ? (
              <TenantSecurityPanel
                apiBaseUrl={apiBaseUrl}
                token={session.accessToken}
                tenantId={effectiveAdminTenantId}
                tenantName={effectiveAdminTenantName}
              />
            ) : null}

            {dashboardTab === "notifications" ? (
              <NotificationsPanel
                invitations={tenantInvitations}
                loading={loadingInvitations}
                actionInvitationId={actionInvitationId}
                onAccept={acceptTenantInvitation}
                onReject={rejectTenantInvitation}
              />
            ) : null}

            {dashboardTab === "take" ? (
              <PracticeCenter embedded />
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
          </div>
        </div>
      </section>
    </main>
  );
}
