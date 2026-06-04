"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";

type Session = {
  email?: string;
  accessToken?: string;
};

type StudentQuestionOption = {
  key: string;
  text: string;
};

type StudentTestQuestion = {
  attemptQuestionId: string;
  questionNumber: number;
  questionType: string;
  questionText: string;
  options: StudentQuestionOption[];
  selectedOptionKeys: string[];
  answerText?: string | null;
  correctOptionKeys: string[];
  correctAnswerText?: string | null;
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
  currentQuestion: StudentTestQuestion;
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
      return option ? `${option.key}. ${option.text}` : key;
    })
    .join(", ");
}

function submittedAnswerText(question: StudentTestQuestion) {
  return usesOptions(question.questionType) ? optionText(question, question.selectedOptionKeys ?? []) : question.answerText || "No answer";
}

function correctAnswerText(question: StudentTestQuestion) {
  return usesOptions(question.questionType) ? optionText(question, question.correctOptionKeys ?? []) : question.correctAnswerText || "Not configured";
}

export default function StudentTestPage() {
  const params = useParams<{ attemptId: string }>();
  const router = useRouter();
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";
  const [session, setSession] = useState<Session | null>(null);
  const [attempt, setAttempt] = useState<StudentTestAttempt | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<StudentTestQuestion | null>(null);
  const [selectedOptionKeys, setSelectedOptionKeys] = useState<string[]>([]);
  const [answerText, setAnswerText] = useState("");
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [questionSubmitted, setQuestionSubmitted] = useState(false);
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
    if (!session?.accessToken || !params.attemptId) return;
    loadAttempt();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken, params.attemptId]);

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
    setQuestionSubmitted(currentQuestion.correct != null);
  }, [currentQuestion]);

  const submitted = attempt?.status === "SUBMITTED";
  const currentNavigation = useMemo(() => {
    if (!attempt || !currentQuestion) return null;
    return attempt.navigation.find((item) => item.attemptQuestionId === currentQuestion.attemptQuestionId) ?? null;
  }, [attempt, currentQuestion]);

  async function request(path: string, init?: RequestInit) {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${session?.accessToken ?? ""}`,
        ...(init?.headers ?? {}),
      },
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || body.message || `Request failed with ${response.status}`);
    return body;
  }

  async function loadAttempt() {
    setLoading(true);
    setError("");
    try {
      const body = await request(`/api/student/tests/${params.attemptId}`);
      setAttempt(body);
      setCurrentQuestion(body.currentQuestion);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load test.");
    } finally {
      setLoading(false);
    }
  }

  async function loadQuestion(attemptQuestionId: string) {
    if (!attempt) return;
    setLoading(true);
    setError("");
    try {
      const question = await request(`/api/student/tests/${attempt.attemptId}/questions/${attemptQuestionId}`);
      setCurrentQuestion(question);
      const refreshed = await request(`/api/student/tests/${attempt.attemptId}`);
      setAttempt(refreshed);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load question.");
    } finally {
      setLoading(false);
    }
  }

  async function saveAnswer(nextSelectedOptionKeys = selectedOptionKeys, nextAnswerText = answerText) {
    if (!attempt || !currentQuestion || submitted || questionSubmitted || remainingSeconds <= 0) return;
    setSaving(true);
    setError("");
    try {
      await request(`/api/student/tests/${attempt.attemptId}/questions/${currentQuestion.attemptQuestionId}/answer`, {
        method: "PUT",
        body: JSON.stringify({
          selectedOptionKeys: nextSelectedOptionKeys,
          answerText: nextAnswerText,
        }),
      });
      const refreshed = await request(`/api/student/tests/${attempt.attemptId}`);
      setAttempt(refreshed);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to save answer.");
    } finally {
      setSaving(false);
    }
  }

  async function submitTest() {
    if (!attempt) return;
    setLoading(true);
    setError("");
    try {
      const body = await request(`/api/student/tests/${attempt.attemptId}/submit`, { method: "POST" });
      setAttempt(body);
      setCurrentQuestion(body.currentQuestion);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to submit test.");
    } finally {
      setLoading(false);
    }
  }

  async function submitCurrentQuestion() {
    if (!attempt || !currentQuestion || submitted) return;
    setLoading(true);
    setError("");
    try {
      const question = await request(`/api/student/tests/${attempt.attemptId}/questions/${currentQuestion.attemptQuestionId}/submit`, {
        method: "POST",
        body: JSON.stringify({
          selectedOptionKeys,
          answerText,
        }),
      });
      setCurrentQuestion(question);
      setQuestionSubmitted(true);
      const refreshed = await request(`/api/student/tests/${attempt.attemptId}`);
      setAttempt(refreshed);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to submit answer.");
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

  function nextQuestion() {
    const nextId = nextQuestionId();
    if (nextId) loadQuestion(nextId);
  }

  function toggleOption(key: string) {
    if (!currentQuestion || submitted || questionSubmitted) return;
    let nextKeys: string[];
    if (currentQuestion.questionType === "MULTIPLE_SELECT") {
      nextKeys = selectedOptionKeys.includes(key) ? selectedOptionKeys.filter((value) => value !== key) : [...selectedOptionKeys, key];
    } else {
      nextKeys = [key];
    }
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
        <a className="back-link" href="/practice">&lt;- Back to test search</a>
        <div className="test-taking-header">
          <div>
            <div className="eyebrow">Student test</div>
            <h1>{attempt?.testName ?? "Loading test"}</h1>
          </div>
          <div className={remainingSeconds <= 30 && !submitted ? "test-timer urgent" : "test-timer"}>
            <strong>{submitted ? "Submitted" : formatRemaining(remainingSeconds)}</strong>
            <span>{attempt?.difficulty ?? ""}</span>
          </div>
        </div>

        <div className="dashboard-actions test-top-actions">
          {!submitted ? (
            <button type="button" className="primary-button" disabled={loading} onClick={submitTest}>Submit test</button>
          ) : null}
        </div>

        {error ? <p className="notice error test-page-notice">{error}</p> : null}

        {submitted && attempt ? (
          <p className="notice success test-page-notice">Final score: {attempt.scorePoints ?? 0} / {attempt.maxPoints}</p>
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

        {submitted && attempt ? (
          <div className="test-review-list">
            {(attempt.questions ?? []).map((question) => (
              <section className="test-question-card" key={question.attemptQuestionId}>
                <div className="test-question-meta">
                  <span>Question {question.questionNumber} of {attempt.questionCount}</span>
                  <span>{question.questionType.replaceAll("_", " ")}</span>
                </div>
                <h2>{question.questionText}</h2>
                {usesOptions(question.questionType) ? (
                  <div className="practice-options">
                    {question.options.map((option) => {
                      const selectedOption = question.selectedOptionKeys.includes(option.key);
                      const correctOption = question.correctOptionKeys.includes(option.key);
                      const className = [
                        "practice-option",
                        selectedOption ? "active" : "",
                        correctOption ? "correct-option" : "",
                      ].filter(Boolean).join(" ");
                      return (
                        <button type="button" key={option.key} disabled className={className}>
                          <input
                            type={question.questionType === "MULTIPLE_SELECT" ? "checkbox" : "radio"}
                            checked={selectedOption}
                            readOnly
                          />
                          <span>{option.key}</span>
                          <span>{option.text}</span>
                        </button>
                      );
                    })}
                  </div>
                ) : null}
                <div className="test-answer-review">
                  <p><strong>Your answer:</strong> {submittedAnswerText(question)}</p>
                  <p><strong>Correct answer:</strong> {correctAnswerText(question)}</p>
                </div>
                <p className={question.correct ? "notice success" : "notice error"}>
                  {question.correct ? "Correct." : "Incorrect."}
                </p>
              </section>
            ))}
          </div>
        ) : currentQuestion ? (
          <section className="test-question-card">
            <div className="test-question-meta">
              <span>Question {currentQuestion.questionNumber} of {attempt?.questionCount ?? "?"}</span>
              <span>{currentQuestion.questionType.replaceAll("_", " ")}</span>
            </div>
            <h2>{currentQuestion.questionText}</h2>
            {usesOptions(currentQuestion.questionType) ? (
              <div className="practice-options">
                {currentQuestion.options.map((option) => (
                  <button
                    type="button"
                    key={option.key}
                    disabled={submitted || questionSubmitted}
                    className={selectedOptionKeys.includes(option.key) ? "practice-option active" : "practice-option"}
                    onClick={() => toggleOption(option.key)}
                  >
                    <input
                      type={currentQuestion.questionType === "MULTIPLE_SELECT" ? "checkbox" : "radio"}
                      checked={selectedOptionKeys.includes(option.key)}
                      readOnly
                    />
                    <span>{option.key}</span>
                    <span>{option.text}</span>
                  </button>
                ))}
              </div>
            ) : (
              <label className="student-search">
                Answer
                <input value={answerText} disabled={submitted || questionSubmitted} onChange={(event) => changeAnswerText(event.target.value)} />
              </label>
            )}
            {(questionSubmitted || submitted) && (currentQuestion.correct ?? currentNavigation?.correct) != null ? (
              <p className={(currentQuestion.correct ?? currentNavigation?.correct) ? "notice success" : "notice error"}>
                {(currentQuestion.correct ?? currentNavigation?.correct) ? "Correct." : "Incorrect."}
              </p>
            ) : null}
          </section>
        ) : null}

        {!submitted ? (
          <div className="dashboard-actions">
            <button type="button" className="primary-button" disabled={loading || saving || questionSubmitted} onClick={submitCurrentQuestion}>
              Submit
            </button>
            <button type="button" className="secondary-button" disabled={loading || !nextQuestionId()} onClick={nextQuestion}>
              Next
            </button>
          </div>
        ) : null}
      </section>
    </main>
  );
}
