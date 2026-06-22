"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

type Session = {
  email?: string;
  accessToken?: string;
  refreshToken?: string;
};

type StudentQuestionOption = {
  key: string;
  text: string;
  mediaObjectKey?: string | null;
  mediaContentType?: string | null;
};

type StudentTestQuestion = {
  attemptQuestionId: string;
  questionNumber: number;
  questionType: string;
  questionText: string;
  questionMediaObjectKey?: string | null;
  questionMediaContentType?: string | null;
  options: StudentQuestionOption[];
  selectedOptionKeys: string[];
  answerText?: string | null;
  correctOptionKeys: string[];
  correctAnswerText?: string | null;
  correctAnswerMediaObjectKey?: string | null;
  correctAnswerMediaContentType?: string | null;
  correct?: boolean | null;
};

type StudentTestNavigationItem = {
  attemptQuestionId: string;
  questionNumber: number;
  answered: boolean;
  correct?: boolean | null;
};

type StudentTestAttempt = {
  attemptId: string;
  testName: string;
  difficulty: string;
  status: string;
  startedAt: string;
  expiresAt: string;
  submittedAt?: string | null;
  questionCount: number;
  scorePoints?: number | null;
  maxPoints: number;
  navigation: StudentTestNavigationItem[];
  currentQuestion?: StudentTestQuestion | null;
  questions: StudentTestQuestion[];
};

function readStoredSession() {
  return localStorage.getItem("clearleaf.auth");
}

function removeStoredSession() {
  localStorage.removeItem("clearleaf.auth");
}

function formatRemaining(seconds: number) {
  const safe = Math.max(0, seconds);
  const minutes = Math.floor(safe / 60);
  const rest = safe % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

function usesOptions(questionType: string) {
  return ["SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE"].includes(questionType);
}

function optionText(question: StudentTestQuestion, keys: string[]) {
  if (!keys.length) return "No answer";
  return keys
    .map((key) => {
      const option = question.options.find((item) => item.key === key);
      return option ? `${option.key}. ${option.text || (option.mediaObjectKey ? "Image option" : "")}` : key;
    })
    .join(", ");
}

function submittedAnswerText(question: StudentTestQuestion) {
  return usesOptions(question.questionType) ? optionText(question, question.selectedOptionKeys ?? []) : question.answerText || "No answer";
}

function correctAnswerText(question: StudentTestQuestion) {
  return usesOptions(question.questionType)
    ? optionText(question, question.correctOptionKeys ?? [])
    : question.correctAnswerText || (question.correctAnswerMediaObjectKey ? "" : "Not configured");
}

function questionTitle(question: StudentTestQuestion) {
  return question.questionText?.trim() || (question.questionMediaObjectKey ? "Image question" : "Question");
}

function StudentMedia({ objectKey, token, alt, className = "test-media" }: { objectKey?: string | null; token?: string; alt: string; className?: string }) {
  const [source, setSource] = useState("");

  useEffect(() => {
    if (!objectKey || !token) {
      setSource("");
      return;
    }
    let revoked = false;
    let objectUrl = "";
    fetch(`${apiBaseUrl}/api/student/media?objectKey=${encodeURIComponent(objectKey)}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((response) => {
        if (!response.ok) throw new Error(`Request failed with ${response.status}`);
        return response.blob();
      })
      .then((blob) => {
        if (revoked) return;
        objectUrl = URL.createObjectURL(blob);
        setSource(objectUrl);
      })
      .catch(() => setSource(""));
    return () => {
      revoked = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [objectKey, token]);

  if (!objectKey) return null;
  if (!source) return <span className="media-loading">Loading image...</span>;
  return <img className={className} src={source} alt={alt} />;
}

export default function AssignedTestPage() {
  const params = useParams<{ assignmentId: string }>();
  const router = useRouter();
  const [session, setSession] = useState<Session | null>(null);
  const [attempt, setAttempt] = useState<StudentTestAttempt | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<StudentTestQuestion | null>(null);
  const [selectedOptionKeys, setSelectedOptionKeys] = useState<string[]>([]);
  const [answerText, setAnswerText] = useState("");
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const answerTextSaveTimeout = useRef<number | null>(null);

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

  useEffect(() => {
    if (!session?.accessToken || !params.assignmentId) return;
    loadAttempt();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken, params.assignmentId]);

  useEffect(() => {
    if (!attempt) return;
    const expiresAtValue = attempt.expiresAt;
    function updateRemaining() {
      const expiresAt = new Date(expiresAtValue).getTime();
      setRemainingSeconds(Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)));
    }
    updateRemaining();
    const interval = window.setInterval(updateRemaining, 1000);
    return () => window.clearInterval(interval);
  }, [attempt]);

  useEffect(() => {
    if (!currentQuestion) return;
    setSelectedOptionKeys(currentQuestion.selectedOptionKeys ?? []);
    setAnswerText(currentQuestion.answerText ?? "");
  }, [currentQuestion]);

  const submitted = attempt?.status === "SUBMITTED";
  const hasPublishedReview = submitted && Boolean(attempt?.questions?.length);

  function persistSession(nextSession: Session) {
    localStorage.setItem("clearleaf.auth", JSON.stringify(nextSession));
    setSession(nextSession);
  }

  async function refreshSession() {
    if (!session?.refreshToken) {
      removeStoredSession();
      router.replace("/account");
      throw new Error("Session expired. Please sign in again.");
    }
    const response = await fetch(`${apiBaseUrl}/api/public/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok || !body.accessToken) {
      removeStoredSession();
      router.replace("/account");
      throw new Error(body.error || body.message || "Session expired. Please sign in again.");
    }
    const nextSession = {
      ...session,
      email: body.email || session.email,
      accessToken: body.accessToken,
      refreshToken: body.refreshToken || session.refreshToken,
    };
    persistSession(nextSession);
    return nextSession.accessToken;
  }

  async function request(path: string, init?: RequestInit, retry = true) {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${session?.accessToken ?? ""}`,
        ...(init?.headers ?? {}),
      },
    });
    const body = await response.json().catch(() => ({}));
    if (response.status === 401 && retry) {
      const accessToken = await refreshSession();
      return request(path, {
        ...init,
        headers: {
          ...(init?.headers ?? {}),
          Authorization: `Bearer ${accessToken}`,
        },
      }, false);
    }
    if (!response.ok) throw new Error(body.error || body.message || `Request failed with ${response.status}`);
    return body;
  }

  async function loadAttempt() {
    setLoading(true);
    setError("");
    try {
      const body = await request(`/api/student/assigned-tests/${params.assignmentId}`);
      setAttempt(body);
      setCurrentQuestion(body.currentQuestion ?? body.questions?.[0] ?? null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load assigned test.");
    } finally {
      setLoading(false);
    }
  }

  async function loadQuestion(attemptQuestionId: string) {
    setLoading(true);
    setError("");
    try {
      const question = await request(`/api/student/assigned-tests/${params.assignmentId}/questions/${attemptQuestionId}`);
      setCurrentQuestion(question);
      const refreshed = await request(`/api/student/assigned-tests/${params.assignmentId}`);
      setAttempt(refreshed);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load question.");
    } finally {
      setLoading(false);
    }
  }

  async function saveAnswer(nextSelectedOptionKeys = selectedOptionKeys, nextAnswerText = answerText) {
    if (!attempt || !currentQuestion || submitted || remainingSeconds <= 0) return;
    setSaving(true);
    setError("");
    try {
      await request(`/api/student/assigned-tests/${params.assignmentId}/questions/${currentQuestion.attemptQuestionId}/answer`, {
        method: "PUT",
        body: JSON.stringify({
          selectedOptionKeys: nextSelectedOptionKeys,
          answerText: nextAnswerText,
        }),
      });
      const refreshed = await request(`/api/student/assigned-tests/${params.assignmentId}`);
      setAttempt(refreshed);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to save answer.");
    } finally {
      setSaving(false);
    }
  }

  async function submitTest() {
    setLoading(true);
    setError("");
    try {
      const body = await request(`/api/student/assigned-tests/${params.assignmentId}/submit`, { method: "POST" });
      setAttempt(body);
      setCurrentQuestion(body.currentQuestion ?? null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to submit assigned test.");
    } finally {
      setLoading(false);
    }
  }

  function nextQuestionId() {
    if (!attempt || !currentQuestion) return "";
    const currentIndex = attempt.navigation.findIndex((item) => item.attemptQuestionId === currentQuestion.attemptQuestionId);
    if (currentIndex < 0 || currentIndex >= attempt.navigation.length - 1) return "";
    return attempt.navigation[currentIndex + 1].attemptQuestionId;
  }

  function toggleOption(key: string) {
    if (!currentQuestion || submitted) return;
    const nextKeys = currentQuestion.questionType === "MULTIPLE_SELECT"
      ? (selectedOptionKeys.includes(key) ? selectedOptionKeys.filter((value) => value !== key) : [...selectedOptionKeys, key])
      : [key];
    setSelectedOptionKeys(nextKeys);
    saveAnswer(nextKeys, answerText);
  }

  function changeAnswerText(value: string) {
    setAnswerText(value);
    if (answerTextSaveTimeout.current) {
      window.clearTimeout(answerTextSaveTimeout.current);
    }
    answerTextSaveTimeout.current = window.setTimeout(() => saveAnswer(selectedOptionKeys, value), 350);
  }

  return (
    <main className="student-shell">
      <section className="student-panel test-taking-panel">
        <a className="secondary-button compact-button page-nav-button" href="/dashboard">Dashboard</a>
        <div className="test-taking-header">
          <div>
            <div className="eyebrow">Assigned test</div>
            <h1>{attempt?.testName ?? "Loading assigned test"}</h1>
          </div>
          <div className={remainingSeconds <= 30 && !submitted ? "test-timer urgent" : "test-timer"}>
            <strong>{submitted ? "Submitted" : formatRemaining(remainingSeconds)}</strong>
            <span>{saving ? "Saving..." : "Autosaved"}</span>
          </div>
        </div>

        {error ? <p className="notice error test-page-notice">{error}</p> : null}

        {!submitted ? (
          <div className="dashboard-actions test-top-actions">
            <button type="button" className="primary-button" disabled={loading} onClick={submitTest}>Submit test</button>
          </div>
        ) : hasPublishedReview && attempt ? (
          <p className="notice success test-page-notice">Final score: {attempt.scorePoints ?? 0} / {attempt.maxPoints}</p>
        ) : submitted ? (
          <p className="notice warning test-page-notice">Test submitted. Results will be visible after the admin publishes them.</p>
        ) : null}

        {attempt && !submitted ? (
          <div className="test-question-nav">
            {attempt.navigation.map((item) => (
              <button
                type="button"
                key={item.attemptQuestionId}
                className={item.attemptQuestionId === currentQuestion?.attemptQuestionId ? "question-nav-button active" : "question-nav-button"}
                onClick={() => loadQuestion(item.attemptQuestionId)}
              >
                {item.questionNumber}
                {item.answered ? <span /> : null}
              </button>
            ))}
          </div>
        ) : null}

        {hasPublishedReview && attempt ? (
          <div className="test-review-list">
            {attempt.questions.map((question) => (
              <section className="test-question-card" key={question.attemptQuestionId}>
                <div className="test-question-meta">
                  <span>Question {question.questionNumber} of {attempt.questionCount}</span>
                  <span>{question.questionType.replaceAll("_", " ")}</span>
                </div>
                <h2>{questionTitle(question)}</h2>
                <StudentMedia objectKey={question.questionMediaObjectKey} token={session?.accessToken} alt="Question image" />
                {usesOptions(question.questionType) ? (
                  <div className="practice-options">
                    {question.options.map((option) => {
                      const selectedOption = question.selectedOptionKeys.includes(option.key);
                      const correctOption = question.correctOptionKeys.includes(option.key);
                      const className = ["practice-option", selectedOption ? "active" : "", correctOption ? "correct-option" : ""].filter(Boolean).join(" ");
                      return (
                        <button type="button" key={option.key} disabled className={className}>
                          <input type={question.questionType === "MULTIPLE_SELECT" ? "checkbox" : "radio"} checked={selectedOption} readOnly />
                          <span>{option.key}</span>
                          {option.text ? <span>{option.text}</span> : null}
                          <StudentMedia objectKey={option.mediaObjectKey} token={session?.accessToken} alt={`Option ${option.key} image`} className="option-test-media" />
                        </button>
                      );
                    })}
                  </div>
                ) : null}
                <div className="test-answer-review">
                  <p><strong>Your answer:</strong> {submittedAnswerText(question)}</p>
                  <p><strong>Correct answer:</strong> {correctAnswerText(question)}</p>
                  <StudentMedia objectKey={question.correctAnswerMediaObjectKey} token={session?.accessToken} alt="Correct answer image" />
                </div>
                <p className={question.correct ? "notice success" : "notice error"}>{question.correct ? "Correct." : "Incorrect."}</p>
              </section>
            ))}
          </div>
        ) : currentQuestion && !submitted ? (
          <section className="test-question-card">
            <div className="test-question-meta">
              <span>Question {currentQuestion.questionNumber} of {attempt?.questionCount ?? "?"}</span>
              <span>{currentQuestion.questionType.replaceAll("_", " ")}</span>
            </div>
            <h2>{questionTitle(currentQuestion)}</h2>
            <StudentMedia objectKey={currentQuestion.questionMediaObjectKey} token={session?.accessToken} alt="Question image" />
            {usesOptions(currentQuestion.questionType) ? (
              <div className="practice-options">
                {currentQuestion.options.map((option) => (
                  <button
                    type="button"
                    key={option.key}
                    disabled={submitted}
                    className={selectedOptionKeys.includes(option.key) ? "practice-option active" : "practice-option"}
                    onClick={() => toggleOption(option.key)}
                  >
                    <input type={currentQuestion.questionType === "MULTIPLE_SELECT" ? "checkbox" : "radio"} checked={selectedOptionKeys.includes(option.key)} readOnly />
                    <span>{option.key}</span>
                    {option.text ? <span>{option.text}</span> : null}
                    <StudentMedia objectKey={option.mediaObjectKey} token={session?.accessToken} alt={`Option ${option.key} image`} className="option-test-media" />
                  </button>
                ))}
              </div>
            ) : (
              <label className="student-search">
                Answer
                <input value={answerText} disabled={submitted} onChange={(event) => changeAnswerText(event.target.value)} />
              </label>
            )}
          </section>
        ) : null}

        {!submitted ? (
          <div className="dashboard-actions">
            <button type="button" className="secondary-button" disabled={loading || !nextQuestionId()} onClick={() => {
              const nextId = nextQuestionId();
              if (nextId) loadQuestion(nextId);
            }}>Next</button>
            <button type="button" className="primary-button" disabled={loading} onClick={submitTest}>Submit test</button>
          </div>
        ) : null}
      </section>
    </main>
  );
}
