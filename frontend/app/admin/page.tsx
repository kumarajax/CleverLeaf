"use client";

import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

type Session = {
  email?: string;
  accessToken?: string;
  refreshToken?: string;
};

type JwtPayload = {
  email?: string;
  name?: string;
  realm_access?: { roles?: string[] };
};

type MeResponse = {
  email?: string;
  name?: string;
  roles?: string[];
};

type TaxonomyNode = {
  id: string;
  levelTypeId: string;
  parentId: string | null;
  nodeKey: string;
  displayName: string;
  status: string;
  sortOrder: number;
};

type TaxonomyLevelType = {
  id: string;
  levelKey: string;
  displayName: string;
  allowedParentKey: string | null;
  sortOrder: number;
  active: boolean;
};

type QuestionOption = {
  key: string;
  text: string;
  correct: boolean;
};

type AdminQuestion = {
  id: string;
  taxonomyNodeId: string;
  taxonomyNodeLabel: string;
  taxonomyNodeStatus: string;
  questionType: string;
  difficulty: string;
  workflowStatus: string;
  questionText: string;
  explanation?: string | null;
  sourceReference?: string | null;
  licenseCategory?: string | null;
  options: QuestionOption[];
};

type CsvRow = {
  lineNumber: number;
  questionText?: string | null;
  type?: string | null;
  difficulty?: string | null;
  workflowStatus?: string | null;
  valid?: boolean;
  errors?: string[];
};

type CsvPreviewResponse = {
  objectKey: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  rows: CsvRow[];
};

type CsvImportSummary = {
  objectKey: string;
  importedRows: number;
  failedRows: number;
  rows: CsvRow[];
};

type TreeNode = TaxonomyNode & { children: TreeNode[] };

type TaxonomyFormState = {
  id: string;
  levelKey: string;
  parentId: string;
  nodeKey: string;
  displayName: string;
  sortOrder: number;
  status: string;
};

type QuestionFormState = {
  id: string;
  taxonomyNodeId: string;
  actor: string;
  questionType: string;
  difficulty: string;
  workflowStatus: string;
  questionText: string;
  explanation: string;
  sourceReference: string;
  licenseCategory: string;
  options: QuestionOption[];
};

const questionTypes = ["SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE", "FILL_BLANK", "NUMERICAL"];
const difficulties = ["EASY", "MEDIUM", "HARD"];
const workflowStatuses = ["DRAFT", "MISSING_ANSWER", "MISSING_EXPLANATION", "AI_GENERATED", "PENDING_REVIEW", "APPROVED", "READY_FOR_TEST", "ARCHIVED", "REJECTED"];
const taxonomyStatuses = ["ACTIVE", "INACTIVE", "ALL"];

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

function blankOptions(): QuestionOption[] {
  return [
    { key: "A", text: "", correct: true },
    { key: "B", text: "", correct: false },
    { key: "C", text: "", correct: false },
    { key: "D", text: "", correct: false },
  ];
}

function buildTree(nodes: TaxonomyNode[]): TreeNode[] {
  const byParent = new Map<string, TreeNode[]>();
  const indexed = new Map<string, TreeNode>();
  nodes.forEach((node) => indexed.set(node.id, { ...node, children: [] }));
  indexed.forEach((node) => {
    const parentKey = node.parentId ?? "__root__";
    const bucket = byParent.get(parentKey) ?? [];
    bucket.push(node);
    byParent.set(parentKey, bucket);
  });
  byParent.forEach((children) => children.sort((left, right) => left.sortOrder - right.sortOrder || left.displayName.localeCompare(right.displayName)));
  indexed.forEach((node) => {
    node.children = byParent.get(node.id) ?? [];
  });
  return byParent.get("__root__") ?? [];
}

function flattenTree(nodes: TreeNode[], depth = 0): Array<{ node: TaxonomyNode; depth: number }> {
  const rows: Array<{ node: TaxonomyNode; depth: number }> = [];
  for (const node of nodes) {
    rows.push({ node, depth });
    rows.push(...flattenTree(node.children, depth + 1));
  }
  return rows;
}

async function readMessage(response: Response) {
  try {
    const body = await response.json();
    return body.message || body.error || `Request failed with ${response.status}`;
  } catch {
    return `Request failed with ${response.status}`;
  }
}

function readStoredSession() {
  const current = localStorage.getItem("clearleaf.auth");
  if (current) return current;
  const accessToken = localStorage.getItem("owl_access_token");
  const idToken = localStorage.getItem("owl_id_token");
  if (!accessToken && !idToken) return null;
  return JSON.stringify({
    email: "",
    accessToken: accessToken ?? "",
    refreshToken: idToken ?? "",
  });
}

function removeStoredSession() {
  localStorage.removeItem("clearleaf.auth");
  localStorage.removeItem("owl_access_token");
  localStorage.removeItem("owl_id_token");
}

export default function AdminPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [session, setSession] = useState<Session | null>(null);
  const [me, setMe] = useState<MeResponse | null>(null);
  const [roles, setRoles] = useState<string[]>([]);
  const [levelTypes, setLevelTypes] = useState<TaxonomyLevelType[]>([]);
  const [allNodes, setAllNodes] = useState<TaxonomyNode[]>([]);
  const [taxonomyNodes, setTaxonomyNodes] = useState<TaxonomyNode[]>([]);
  const [taxonomyFilter, setTaxonomyFilter] = useState("ACTIVE");
  const [selectedTaxonomyNodeId, setSelectedTaxonomyNodeId] = useState("");
  const [taxonomyForm, setTaxonomyForm] = useState<TaxonomyFormState>({
    id: "",
    levelKey: "GRADE",
    parentId: "",
    nodeKey: "",
    displayName: "",
    sortOrder: 1,
    status: "ACTIVE",
  });
  const [questions, setQuestions] = useState<AdminQuestion[]>([]);
  const [questionSearch, setQuestionSearch] = useState("");
  const [questionForm, setQuestionForm] = useState<QuestionFormState>({
    id: "",
    taxonomyNodeId: "",
    actor: "",
    questionType: "SINGLE_SELECT",
    difficulty: "MEDIUM",
    workflowStatus: "DRAFT",
    questionText: "",
    explanation: "",
    sourceReference: "",
    licenseCategory: "CC-BY",
    options: blankOptions(),
  });
  const [preview, setPreview] = useState<CsvPreviewResponse | null>(null);
  const [csvObjectKey, setCsvObjectKey] = useState("");
  const [csvImportSummary, setCsvImportSummary] = useState<CsvImportSummary | null>(null);
  const [csvError, setCsvError] = useState("");
  const [scoreResult, setScoreResult] = useState("");
  const [validationResult, setValidationResult] = useState("");
  const [activeTab, setActiveTab] = useState<"taxonomy" | "manual" | "csv" | "score">("taxonomy");
  const [expandedTaxonomyIds, setExpandedTaxonomyIds] = useState<string[]>([]);
  const [expandedQuestionIds, setExpandedQuestionIds] = useState<string[]>([]);

  const currentToken = session?.accessToken ?? "";
  const levelTypeById = useMemo(() => new Map(levelTypes.map((level) => [level.id, level])), [levelTypes]);
  const nodeById = useMemo(() => new Map(allNodes.map((node) => [node.id, node])), [allNodes]);
  const tree = useMemo(() => buildTree(taxonomyNodes), [taxonomyNodes]);
  const expandedTaxonomySet = useMemo(() => new Set(expandedTaxonomyIds), [expandedTaxonomyIds]);
  const expandedQuestionSet = useMemo(() => new Set(expandedQuestionIds), [expandedQuestionIds]);
  const selectedTaxonomyNode = allNodes.find((node) => node.id === selectedTaxonomyNodeId) ?? null;
  const questionTaxonomyOptions = useMemo(() => {
    const contextNodeId = selectedTaxonomyNodeId || questionForm.taxonomyNodeId;
    const rootId = contextNodeId ? getBranchRootId(contextNodeId) : "";
    return [...allNodes]
      .filter((node) => node.status === "ACTIVE")
      .filter((node) => {
        if (!rootId) return true;
        return getBranchRootId(node.id) === rootId;
      })
      .sort((left, right) => left.displayName.localeCompare(right.displayName));
  }, [allNodes, questionForm.taxonomyNodeId, selectedTaxonomyNodeId]);
  const filteredQuestions = useMemo(() => {
    const search = questionSearch.trim().toLowerCase();
    if (!search) return questions;
    return questions.filter((question) =>
      [
        question.taxonomyNodeLabel,
        question.questionType,
        question.difficulty,
        question.workflowStatus,
        question.questionText,
      ].some((value) => (value ?? "").toLowerCase().includes(search))
    );
  }, [questionSearch, questions]);

  function authHeaders(token = currentToken): Record<string, string> {
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  function isAdmin(payloadRoles: string[]) {
    return payloadRoles.includes("administrator");
  }

  async function loadTaxonomy(filter: string, token = currentToken) {
    const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes?status=${encodeURIComponent(filter)}`, {
      headers: authHeaders(token),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setTaxonomyNodes(body);
    setSelectedTaxonomyNodeId((current) => (body.some((node: TaxonomyNode) => node.id === current) ? current : (body.length > 0 ? body[0].id : "")));
  }

  async function loadAllTaxonomy(token = currentToken) {
    const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes?status=ALL`, {
      headers: authHeaders(token),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setAllNodes(body);
  }

  async function loadLevelTypes(token = currentToken) {
    const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/level-types`, {
      headers: authHeaders(token),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setLevelTypes(body);
  }

  async function loadQuestions(token = currentToken) {
    const response = await fetch(`${apiBaseUrl}/api/admin/questions`, {
      headers: authHeaders(token),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setQuestions(body);
  }

  async function bootstrap() {
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
      const payload = decodePayload(parsed.accessToken);
      const payloadRoles = payload?.realm_access?.roles ?? [];
      if (!isAdmin(payloadRoles)) {
        router.replace("/dashboard");
        return;
      }
      setRoles(payloadRoles);
      setQuestionForm((current) => ({ ...current, actor: parsed.email ?? payload?.email ?? current.actor }));
      const [meResponse, levelResponse] = await Promise.all([
        fetch(`${apiBaseUrl}/api/me`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/admin/taxonomy/level-types`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
      ]);
      const meBody = await meResponse.json().catch(() => ({}));
      const levelBody = await levelResponse.json().catch(() => []);
      if (!meResponse.ok) {
        throw new Error(meBody.error || `Request failed with ${meResponse.status}`);
      }
      if (!levelResponse.ok) {
        throw new Error(levelBody.error || `Request failed with ${levelResponse.status}`);
      }
      setMe(meBody);
      setRoles(meBody.roles ?? payloadRoles);
      setLevelTypes(levelBody);
      const initialLoads = await Promise.allSettled([
        loadAllTaxonomy(parsed.accessToken),
        loadTaxonomy("ACTIVE", parsed.accessToken),
        loadQuestions(parsed.accessToken),
      ]);
      const failures = initialLoads.filter((result): result is PromiseRejectedResult => result.status === "rejected");
      if (failures.length > 0) {
        const reason = failures[0].reason;
        setError(reason instanceof Error ? reason.message : "Unable to load admin console data.");
      }
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : "Unable to load admin console.";
      if (message.toLowerCase().includes("unauthor") || message.toLowerCase().includes("forbid")) {
        removeStoredSession();
        router.replace("/account");
        return;
      }
      setError(message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    bootstrap();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!currentToken) return;
    loadTaxonomy(taxonomyFilter).catch((exception) =>
      setError(exception instanceof Error ? exception.message : "Unable to load taxonomy.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taxonomyFilter, currentToken]);

  useEffect(() => {
    if (!questionForm.taxonomyNodeId && selectedTaxonomyNodeId) {
      setQuestionForm((current) => ({ ...current, taxonomyNodeId: selectedTaxonomyNodeId }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTaxonomyNodeId]);

  useEffect(() => {
    if (taxonomyForm.id) return;
    const defaultParentId = getDefaultParentId(taxonomyForm.levelKey, selectedTaxonomyNodeId);
    setTaxonomyForm((current) => {
      if (current.parentId === defaultParentId) return current;
      return { ...current, parentId: defaultParentId };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taxonomyForm.levelKey, selectedTaxonomyNodeId, allNodes, levelTypes]);

  useEffect(() => {
    if (!tree.length) return;
    setExpandedTaxonomyIds((current) => (current.length ? current : tree.map((node) => node.id)));
  }, [tree]);

  function resetTaxonomyForm() {
    const defaultParentId = getDefaultParentId("GRADE", selectedTaxonomyNodeId);
    setTaxonomyForm({
      id: "",
      levelKey: "GRADE",
      parentId: defaultParentId,
      nodeKey: "",
      displayName: "",
      sortOrder: 1,
      status: "ACTIVE",
    });
  }

  function toggleExpandedTaxonomy(nodeId: string) {
    setExpandedTaxonomyIds((current) => (
      current.includes(nodeId)
        ? current.filter((value) => value !== nodeId)
        : [...current, nodeId]
    ));
  }

  function toggleExpandedQuestion(questionId: string) {
    setExpandedQuestionIds((current) => (
      current.includes(questionId)
        ? current.filter((value) => value !== questionId)
        : [...current, questionId]
    ));
  }

  function loadTaxonomyIntoForm(node: TaxonomyNode) {
    const levelKey = levelTypeById.get(node.levelTypeId)?.levelKey ?? "GRADE";
    setTaxonomyForm({
      id: node.id,
      levelKey,
      parentId: node.parentId ?? "",
      nodeKey: node.nodeKey,
      displayName: node.displayName,
      sortOrder: node.sortOrder,
      status: node.status,
    });
    setSelectedTaxonomyNodeId(node.id);
  }

  function getAncestorChain(nodeId: string) {
    const chain: TaxonomyNode[] = [];
    let current = nodeById.get(nodeId) ?? null;
    while (current) {
      chain.unshift(current);
      current = current.parentId ? nodeById.get(current.parentId) ?? null : null;
    }
    return chain;
  }

  function getBranchRootId(contextNodeId: string) {
    return getAncestorChain(contextNodeId)[0]?.id ?? "";
  }

  function getDefaultParentOptions(levelKey: string, contextNodeId: string) {
    const allowedParentKey = levelTypes.find((level) => level.levelKey === levelKey)?.allowedParentKey ?? null;
    if (!allowedParentKey || !contextNodeId) return [];
    const branchRootId = getBranchRootId(contextNodeId);
    return allNodes.filter((node) => {
      if (node.status !== "ACTIVE") return false;
      if (levelTypeById.get(node.levelTypeId)?.levelKey !== allowedParentKey) return false;
      return getBranchRootId(node.id) === branchRootId;
    });
  }

  function getDefaultParentId(levelKey: string, contextNodeId: string) {
    return getDefaultParentOptions(levelKey, contextNodeId)[0]?.id ?? "";
  }

  function resetQuestionForm(nextNodeId = selectedTaxonomyNodeId || questionTaxonomyOptions[0]?.id || "") {
    const defaultNodeId = nextNodeId || questionTaxonomyOptions[0]?.id || "";
    setQuestionForm({
      id: "",
      taxonomyNodeId: defaultNodeId,
      actor: me?.email ?? session?.email ?? "",
      questionType: "SINGLE_SELECT",
      difficulty: "MEDIUM",
      workflowStatus: "DRAFT",
      questionText: "",
      explanation: "",
      sourceReference: "",
      licenseCategory: "CC-BY",
      options: blankOptions(),
    });
    setScoreResult("");
    setValidationResult("");
  }

  function loadQuestionIntoForm(question: AdminQuestion) {
    setQuestionForm({
      id: question.id,
      taxonomyNodeId: question.taxonomyNodeId,
      actor: me?.email ?? session?.email ?? "",
      questionType: question.questionType,
      difficulty: question.difficulty,
      workflowStatus: question.workflowStatus,
      questionText: question.questionText,
      explanation: question.explanation ?? "",
      sourceReference: question.sourceReference ?? "",
      licenseCategory: question.licenseCategory ?? "",
      options: question.options.length ? question.options : blankOptions(),
    });
    setActiveTab("manual");
    setSelectedTaxonomyNodeId(question.taxonomyNodeId);
  }

  useEffect(() => {
    if (activeTab !== "manual") return;
    if (questionForm.id) return;
    setQuestionForm((current) => {
      const allowed = questionTaxonomyOptions.some((node) => node.id === current.taxonomyNodeId);
      if (allowed && current.taxonomyNodeId) return current;
      const nextNodeId = selectedTaxonomyNodeId && questionTaxonomyOptions.some((node) => node.id === selectedTaxonomyNodeId)
        ? selectedTaxonomyNodeId
        : questionTaxonomyOptions[0]?.id ?? "";
      if (!nextNodeId || current.taxonomyNodeId === nextNodeId) return current;
      return { ...current, taxonomyNodeId: nextNodeId };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, questionTaxonomyOptions, selectedTaxonomyNodeId]);

  function updateQuestionOptions(nextOptions: QuestionOption[]) {
    setQuestionForm((current) => ({ ...current, options: nextOptions }));
  }

  function deleteQuestionOption(index: number) {
    setQuestionForm((current) => {
      const next = current.options.filter((_, optionIndex) => optionIndex !== index);
      return { ...current, options: next.length ? next : blankOptions() };
    });
  }

  async function deleteQuestion(questionId: string) {
    if (!window.confirm("Delete this question? This cannot be undone.")) return;
    setError("");
    setStatus("");
    try {
      const response = await fetch(`${apiBaseUrl}/api/admin/questions/${questionId}`, {
        method: "DELETE",
        headers: authHeaders(),
      });
      if (!response.ok && response.status !== 204) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.error || `Request failed with ${response.status}`);
      }
      if (questionForm.id === questionId) {
        resetQuestionForm(selectedTaxonomyNodeId);
      }
      setStatus("Question deleted.");
      await loadQuestions();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to delete question.");
    }
  }

  function renderTaxonomyTree(nodes: TreeNode[], depth = 0): ReactNode[] {
    return nodes.flatMap((node) => {
      const hasChildren = node.children.length > 0;
      const isExpanded = expandedTaxonomySet.has(node.id);
      const children = hasChildren && isExpanded ? renderTaxonomyTree(node.children, depth + 1) : [];
      return [
        <div key={node.id} className="taxonomy-tree-row" style={{ marginLeft: `${depth * 18}px` }}>
          <button
            type="button"
            className="tree-toggle"
            disabled={!hasChildren}
            aria-label={hasChildren ? (isExpanded ? "Collapse node" : "Expand node") : "Leaf node"}
            onClick={(event) => {
              event.stopPropagation();
              if (hasChildren) toggleExpandedTaxonomy(node.id);
            }}
          >
            {hasChildren ? (isExpanded ? "−" : "+") : "•"}
          </button>
          <button
            type="button"
            className={selectedTaxonomyNodeId === node.id ? "taxonomy-row active" : "taxonomy-row"}
            onClick={() => loadTaxonomyIntoForm(node)}
          >
            <span className="taxonomy-label">
              <strong>{node.displayName}</strong>
              <span>{node.nodeKey}</span>
            </span>
            <span className="taxonomy-meta">{node.status}</span>
          </button>
        </div>,
        ...children,
      ];
    });
  }

  async function submitTaxonomy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setStatus("");
    try {
      const payload = {
        levelKey: taxonomyForm.levelKey,
        parentId: taxonomyForm.parentId || null,
        nodeKey: taxonomyForm.nodeKey,
        displayName: taxonomyForm.displayName,
        sortOrder: taxonomyForm.sortOrder,
        status: taxonomyForm.status,
      };
      const response = await fetch(
        taxonomyForm.id ? `${apiBaseUrl}/api/admin/taxonomy/nodes/${taxonomyForm.id}` : `${apiBaseUrl}/api/admin/taxonomy/nodes`,
        {
          method: taxonomyForm.id ? "PUT" : "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders(),
          },
          body: JSON.stringify(payload),
        }
      );
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || `Request failed with ${response.status}`);
      }
      setStatus(taxonomyForm.id ? "Taxonomy updated." : "Taxonomy created.");
      resetTaxonomyForm();
      await Promise.all([loadAllTaxonomy(), loadTaxonomy(taxonomyFilter), loadQuestions()]);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to save taxonomy.");
    }
  }

  async function createSampleCaaspp() {
    setError("");
    setStatus("");
    try {
      if (allNodes.some((node) => node.nodeKey === "CAASPP" && node.displayName === "California CAASPP")) {
        setStatus("California CAASPP sample already exists.");
        return;
      }
      const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          levelKey: "CURRICULUM",
          parentId: null,
          nodeKey: "CAASPP",
          displayName: "California CAASPP",
          sortOrder: 2,
        }),
      });
      const curriculum = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(curriculum.error || `Request failed with ${response.status}`);
      }
      const edition = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          levelKey: "EDITION",
          parentId: curriculum.id,
          nodeKey: "2026",
          displayName: "2026 Edition",
          sortOrder: 1,
        }),
      }).then((res) => res.json());
      const grade = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          levelKey: "GRADE",
          parentId: edition.id,
          nodeKey: "5",
          displayName: "Grade 5",
          sortOrder: 1,
        }),
      }).then((res) => res.json());
      const subject = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          levelKey: "SUBJECT",
          parentId: grade.id,
          nodeKey: "MATH",
          displayName: "Math",
          sortOrder: 1,
        }),
      }).then((res) => res.json());
      const chapter = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          levelKey: "CHAPTER",
          parentId: subject.id,
          nodeKey: "FRACTIONS",
          displayName: "Fractions",
          sortOrder: 1,
        }),
      }).then((res) => res.json());
      await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          levelKey: "TOPIC",
          parentId: chapter.id,
          nodeKey: "FRACTION_ADDITION",
          displayName: "Fraction Addition",
          sortOrder: 1,
        }),
      });
      setStatus("California CAASPP taxonomy created.");
      await Promise.all([loadAllTaxonomy(), loadTaxonomy(taxonomyFilter), loadQuestions()]);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to create CAASPP taxonomy.");
    }
  }

  async function submitQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setStatus("");
    try {
      const payload = {
        taxonomyNodeId: questionForm.taxonomyNodeId,
        actor: questionForm.actor,
        question: {
          type: questionForm.questionType,
          difficulty: questionForm.difficulty,
          workflowStatus: questionForm.workflowStatus,
          questionText: questionForm.questionText,
          explanation: questionForm.explanation || null,
          sourceReference: questionForm.sourceReference || null,
          licenseCategory: questionForm.licenseCategory || null,
          options: questionForm.options,
        },
      };
      const response = await fetch(
        questionForm.id ? `${apiBaseUrl}/api/admin/questions/${questionForm.id}` : `${apiBaseUrl}/api/admin/questions`,
        {
          method: questionForm.id ? "PUT" : "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders(),
          },
          body: JSON.stringify(payload),
        }
      );
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || `Request failed with ${response.status}`);
      }
      setStatus(questionForm.id ? "Question updated." : "Question created.");
      resetQuestionForm(questionForm.taxonomyNodeId);
      await loadQuestions();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to save question.");
    }
  }

  async function validateQuestion() {
    setError("");
    const response = await fetch(`${apiBaseUrl}/api/questions/validate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders(),
      },
      body: JSON.stringify({
        type: questionForm.questionType,
        difficulty: questionForm.difficulty,
        workflowStatus: questionForm.workflowStatus,
        questionText: questionForm.questionText,
        explanation: questionForm.explanation,
        sourceReference: questionForm.sourceReference,
        licenseCategory: questionForm.licenseCategory,
        options: questionForm.options,
      }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setValidationResult(body.valid ? "Question is valid." : `Validation errors: ${(body.errors ?? []).join("; ")}`);
  }

  async function scoreQuestion() {
    setError("");
    const response = await fetch(`${apiBaseUrl}/api/questions/score`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders(),
      },
      body: JSON.stringify({
        question: {
          type: questionForm.questionType,
          difficulty: questionForm.difficulty,
          workflowStatus: questionForm.workflowStatus,
          questionText: questionForm.questionText,
          explanation: questionForm.explanation,
          sourceReference: questionForm.sourceReference,
          licenseCategory: questionForm.licenseCategory,
          options: questionForm.options,
        },
        submittedOptionKeys: questionForm.options.filter((option) => option.correct).map((option) => option.key),
      }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setScoreResult(body.correct ? "Correct answer." : "Incorrect answer.");
  }

  async function uploadCsv(file: File) {
    setCsvError("");
    setStatus("");
    setPreview(null);
    setCsvImportSummary(null);
    const formData = new FormData();
    formData.append("file", file);
    const uploadResponse = await fetch(`${apiBaseUrl}/api/admin/media/upload`, {
      method: "POST",
      headers: authHeaders(),
      body: formData,
    });
    const uploadBody = await uploadResponse.json().catch(() => ({}));
    if (!uploadResponse.ok) {
      throw new Error(uploadBody.error || `Request failed with ${uploadResponse.status}`);
    }
    setCsvObjectKey(uploadBody.objectKey);
    const previewResponse = await fetch(`${apiBaseUrl}/api/admin/imports/questions/preview?objectKey=${encodeURIComponent(uploadBody.objectKey)}`, {
      headers: authHeaders(),
    });
    const previewBody = await previewResponse.json().catch(() => ({}));
    if (!previewResponse.ok) {
      throw new Error(previewBody.error || `Request failed with ${previewResponse.status}`);
    }
    setPreview(previewBody);
  }

  async function importCsv() {
    if (!csvObjectKey) return;
    setCsvError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/imports/questions?objectKey=${encodeURIComponent(csvObjectKey)}`, {
      method: "POST",
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setCsvImportSummary(body);
    setStatus(`Imported ${body.importedRows} row(s).`);
  }

  function downloadCsvTemplate() {
    const row = [
      selectedTaxonomyNodeId,
      me?.email ?? session?.email ?? "ajay1@gmail.com",
      "SINGLE_SELECT",
      "MEDIUM",
      "DRAFT",
      "What is 2/3 + 1/2?",
      "Use common denominator 6: 2/3 = 4/6 and 1/2 = 3/6, so the answer is 7/6.",
      "Original ClearLeaf sample",
      "CC-BY",
      "A|7/6|true;B|5/6|false;C|1/6|false;D|2/5|false",
    ].join(",");
    const blob = new Blob([
      "taxonomyNodeId,actor,questionType,difficulty,workflowStatus,questionText,explanation,sourceReference,licenseCategory,options\n" + row + "\n",
    ], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "clearleaf-question-template.csv";
    anchor.click();
    URL.revokeObjectURL(url);
  }

  if (loading) {
    return (
      <main className="account-shell">
        <section className="account-panel">
          <div className="eyebrow">ClearLeaf Admin</div>
          <h1>Loading admin console</h1>
          <p className="lede">Verifying session and loading taxonomy.</p>
        </section>
      </main>
    );
  }

  const allowedParentKey = levelTypes.find((level) => level.levelKey === taxonomyForm.levelKey)?.allowedParentKey ?? null;
  const parentOptions = allowedParentKey ? getDefaultParentOptions(taxonomyForm.levelKey, selectedTaxonomyNodeId) : [];

  return (
    <main className="admin-shell">
      <section className="admin-panel">
        <div className="account-tabs">
          <button type="button" className={activeTab === "taxonomy" ? "tab active" : "tab"} onClick={() => setActiveTab("taxonomy")}>Taxonomy</button>
          <button type="button" className={activeTab === "manual" ? "tab active" : "tab"} onClick={() => setActiveTab("manual")}>Manual question</button>
          <button type="button" className={activeTab === "csv" ? "tab active" : "tab"} onClick={() => setActiveTab("csv")}>CSV import</button>
          <button type="button" className={activeTab === "score" ? "tab active" : "tab"} onClick={() => setActiveTab("score")}>Score sandbox</button>
        </div>

        <div className="session-card">
          <div><strong>Root taxonomy</strong><span>{selectedTaxonomyNode ? getAncestorChain(selectedTaxonomyNode.id)[0]?.displayName ?? "None selected" : "None selected"}</span></div>
          <div><strong>Selected node</strong><span>{selectedTaxonomyNode ? selectedTaxonomyNode.displayName : "None selected"}</span></div>
        </div>

        {activeTab === "taxonomy" ? (
          <section className="section">
            <div className="section-header">
              <h2>Taxonomy explorer</h2>
              <p>Show active nodes by default. Use the filter to switch between active, inactive, or all nodes.</p>
            </div>
            <div className="dashboard-actions">
              <label className="inline-select">
                Filter
                <select value={taxonomyFilter} onChange={(event) => setTaxonomyFilter(event.target.value)}>
                  {taxonomyStatuses.map((value) => (
                    <option key={value} value={value}>{value}</option>
                  ))}
                </select>
              </label>
              <button type="button" className="secondary-button" onClick={createSampleCaaspp}>Create California CAASPP sample</button>
              <button type="button" className="primary-button" onClick={resetTaxonomyForm}>New taxonomy node</button>
            </div>
            <div className="taxonomy-grid">
              <div className="taxonomy-tree">
                {renderTaxonomyTree(tree)}
              </div>
              <div className="card">
                <h3>{taxonomyForm.id ? "Edit taxonomy" : "Create taxonomy"}</h3>
                <form className="account-form" onSubmit={submitTaxonomy}>
                  <div className="form-grid">
                    <label>
                      Level
                      <select value={taxonomyForm.levelKey} onChange={(event) => setTaxonomyForm((current) => ({ ...current, levelKey: event.target.value, parentId: getDefaultParentId(event.target.value, selectedTaxonomyNodeId) }))}>
                        {levelTypes.map((level) => (
                          <option key={level.id} value={level.levelKey}>{level.displayName}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Status
                      <select value={taxonomyForm.status} onChange={(event) => setTaxonomyForm((current) => ({ ...current, status: event.target.value }))}>
                        {["ACTIVE", "INACTIVE", "DRAFT", "RETIRED", "ARCHIVED"].map((value) => (
                          <option key={value} value={value}>{value}</option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <div className="form-grid">
                    <label>
                      Parent
                      <select value={taxonomyForm.parentId} onChange={(event) => setTaxonomyForm((current) => ({ ...current, parentId: event.target.value }))} disabled={!allowedParentKey || parentOptions.length === 0}>
                        <option value="">No parent</option>
                        {parentOptions.map((node) => (
                          <option key={node.id} value={node.id}>{node.displayName}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Sort order
                      <input type="number" value={taxonomyForm.sortOrder} onChange={(event) => setTaxonomyForm((current) => ({ ...current, sortOrder: Number(event.target.value) }))} />
                    </label>
                  </div>
                  <label>
                    Node key
                    <input value={taxonomyForm.nodeKey} onChange={(event) => setTaxonomyForm((current) => ({ ...current, nodeKey: event.target.value }))} />
                  </label>
                  <label>
                    Display name
                    <input value={taxonomyForm.displayName} onChange={(event) => setTaxonomyForm((current) => ({ ...current, displayName: event.target.value }))} />
                  </label>
                  <div className="dashboard-actions">
                    <button type="submit" className="primary-button">{taxonomyForm.id ? "Save changes" : "Create node"}</button>
                    {taxonomyForm.id ? (
                      <button type="button" className="secondary-button" onClick={resetTaxonomyForm}>Cancel edit</button>
                    ) : null}
                  </div>
                </form>
              </div>
            </div>
          </section>
        ) : null}

        {activeTab === "manual" ? (
          <section className="section">
            <div className="section-header">
              <h2>Question bank</h2>
              <p>See every question, then create a new one or edit an existing one. Use the selected taxonomy node as the default target.</p>
            </div>
            <div className="dashboard-actions">
              <label className="inline-select">
                Search
                <input value={questionSearch} onChange={(event) => setQuestionSearch(event.target.value)} placeholder="Search questions" />
              </label>
              <button type="button" className="primary-button" onClick={() => resetQuestionForm(selectedTaxonomyNodeId)}>New question</button>
            </div>
            <div className="split-layout">
              <div className="card table-card">
                <h3>All questions</h3>
                <div className="question-list">
                  {filteredQuestions.map((question) => {
                    const expanded = expandedQuestionSet.has(question.id);
                    return (
                      <div className="question-row-card" key={question.id}>
                        <div className="question-row-summary">
                          <button
                            type="button"
                            className="tree-toggle"
                            aria-label={expanded ? "Collapse question" : "Expand question"}
                            onClick={() => toggleExpandedQuestion(question.id)}
                          >
                            {expanded ? "▾" : "▸"}
                          </button>
                          <button type="button" className="question-summary-button" onClick={() => loadQuestionIntoForm(question)}>
                            <span className="question-summary-line">
                              <strong>{question.taxonomyNodeLabel}</strong>
                              <span>{question.questionType} · {question.difficulty} · {question.workflowStatus}</span>
                            </span>
                            <span className="question-summary-text">{question.questionText}</span>
                          </button>
                          <button type="button" className="secondary-button compact-button" onClick={() => loadQuestionIntoForm(question)}>
                            Edit
                          </button>
                          <button type="button" className="secondary-button compact-button" onClick={() => deleteQuestion(question.id)}>
                            Delete
                          </button>
                        </div>
                        {expanded ? (
                          <div className="question-row-details">
                            <div><strong>Question</strong><p>{question.questionText}</p></div>
                            <div className="question-details-grid">
                              <div><strong>Type</strong><span>{question.questionType}</span></div>
                              <div><strong>Difficulty</strong><span>{question.difficulty}</span></div>
                              <div><strong>Status</strong><span>{question.workflowStatus}</span></div>
                              <div><strong>Taxonomy</strong><span>{question.taxonomyNodeLabel}</span></div>
                            </div>
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              </div>
              <div className="card">
                <h3>{questionForm.id ? "Edit question" : "Create question"}</h3>
                <form className="account-form" onSubmit={submitQuestion}>
                  <div className="form-grid">
                    <label>
                      Taxonomy node
                      <select value={questionForm.taxonomyNodeId} onChange={(event) => setQuestionForm((current) => ({ ...current, taxonomyNodeId: event.target.value }))}>
                        {questionTaxonomyOptions.map((node) => (
                          <option key={node.id} value={node.id}>{node.displayName} ({node.nodeKey}) [{node.status}]</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Actor
                      <input value={questionForm.actor} onChange={(event) => setQuestionForm((current) => ({ ...current, actor: event.target.value }))} />
                    </label>
                  </div>
                  <div className="form-grid">
                    <label>
                      Question type
                      <select value={questionForm.questionType} onChange={(event) => setQuestionForm((current) => ({ ...current, questionType: event.target.value }))}>
                        {questionTypes.map((value) => <option key={value} value={value}>{value}</option>)}
                      </select>
                    </label>
                    <label>
                      Difficulty
                      <select value={questionForm.difficulty} onChange={(event) => setQuestionForm((current) => ({ ...current, difficulty: event.target.value }))}>
                        {difficulties.map((value) => <option key={value} value={value}>{value}</option>)}
                      </select>
                    </label>
                  </div>
                  <label>
                    Workflow status
                    <select value={questionForm.workflowStatus} onChange={(event) => setQuestionForm((current) => ({ ...current, workflowStatus: event.target.value }))}>
                      {workflowStatuses.map((value) => <option key={value} value={value}>{value}</option>)}
                    </select>
                  </label>
                  <label>
                    Question text
                    <textarea value={questionForm.questionText} onChange={(event) => setQuestionForm((current) => ({ ...current, questionText: event.target.value }))} />
                  </label>
                  <div className="form-grid">
                    <label>
                      Explanation
                      <textarea value={questionForm.explanation} onChange={(event) => setQuestionForm((current) => ({ ...current, explanation: event.target.value }))} />
                    </label>
                    <label>
                      Source reference
                      <textarea value={questionForm.sourceReference} onChange={(event) => setQuestionForm((current) => ({ ...current, sourceReference: event.target.value }))} />
                    </label>
                  </div>
                  <label>
                    License category
                    <input value={questionForm.licenseCategory} onChange={(event) => setQuestionForm((current) => ({ ...current, licenseCategory: event.target.value }))} />
                  </label>
                  <div className="option-editor">
                    {questionForm.options.map((option, index) => (
                      <div className="option-row" key={`${questionForm.id || "new"}-${option.key}-${index}`}>
                        <input
                          className="option-key"
                          value={option.key}
                          onChange={(event) => {
                            const next = questionForm.options.slice();
                            next[index] = { ...option, key: event.target.value.toUpperCase() };
                            updateQuestionOptions(next);
                          }}
                        />
                        <input
                          className="option-text"
                          value={option.text}
                          onChange={(event) => {
                            const next = questionForm.options.slice();
                            next[index] = { ...option, text: event.target.value };
                            updateQuestionOptions(next);
                          }}
                        />
                        <label className="check">
                          <input
                            type="checkbox"
                            checked={option.correct}
                            onChange={(event) => {
                              const next = questionForm.options.slice();
                              next[index] = { ...option, correct: event.target.checked };
                              updateQuestionOptions(next);
                            }}
                          />
                          Correct
                        </label>
                        <button type="button" className="secondary-button compact-button" onClick={() => deleteQuestionOption(index)}>
                          Delete option
                        </button>
                      </div>
                    ))}
                  </div>
                  <div className="dashboard-actions">
                    <button type="button" className="secondary-button" onClick={() => setQuestionForm((current) => ({ ...current, options: [...current.options, { key: String.fromCharCode(65 + current.options.length), text: "", correct: false }] }))}>
                      Add option
                    </button>
                    <button type="button" className="secondary-button" onClick={() => validateQuestion().catch((exception) => setError(exception instanceof Error ? exception.message : "Unable to validate question."))}>
                      Validate
                    </button>
                    <button type="submit" className="primary-button">{questionForm.id ? "Save changes" : "Create question"}</button>
                    <button type="button" className="secondary-button" onClick={() => setQuestionForm((current) => ({ ...current, options: blankOptions() }))}>
                      Reset options
                    </button>
                    {questionForm.id ? (
                      <button type="button" className="secondary-button" onClick={() => deleteQuestion(questionForm.id)}>
                        Delete question
                      </button>
                    ) : null}
                  </div>
                </form>
                {validationResult ? <p className="notice success">{validationResult}</p> : null}
                <div className="dashboard-actions">
                  <button type="button" className="secondary-button" onClick={() => scoreQuestion().catch((exception) => setError(exception instanceof Error ? exception.message : "Unable to score question."))}>
                    Score current answer
                  </button>
                  {questionForm.id ? <button type="button" className="secondary-button" onClick={() => resetQuestionForm(questionForm.taxonomyNodeId)}>New question</button> : null}
                </div>
                {scoreResult ? <p className="notice success">{scoreResult}</p> : null}
              </div>
            </div>
          </section>
        ) : null}

        {activeTab === "csv" ? (
          <section className="section">
            <div className="section-header">
              <h2>CSV import</h2>
              <p>Upload a CSV to MinIO, preview it, and import valid rows as draft questions.</p>
            </div>
            <div className="dashboard-actions">
              <button type="button" className="secondary-button" onClick={downloadCsvTemplate}>Download template</button>
            </div>
            <label className="file-drop">
              Upload CSV
              <input type="file" accept=".csv,text/csv" onChange={async (event) => {
                const file = event.target.files?.[0];
                if (!file) return;
                try {
                  await uploadCsv(file);
                } catch (exception) {
                  setCsvError(exception instanceof Error ? exception.message : "Unable to upload CSV.");
                }
              }} />
            </label>
            {csvObjectKey ? <p className="notice success">Stored object: {csvObjectKey}</p> : null}
            {preview ? (
              <div className="card">
                <h3>Preview</h3>
                <p>{preview.validRows} valid row(s), {preview.invalidRows} invalid row(s)</p>
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Line</th>
                        <th>Question</th>
                        <th>Type</th>
                        <th>Difficulty</th>
                        <th>Valid</th>
                        <th>Errors</th>
                      </tr>
                    </thead>
                    <tbody>
                      {preview.rows.map((row) => (
                        <tr key={row.lineNumber}>
                          <td>{row.lineNumber}</td>
                          <td>{row.questionText}</td>
                          <td>{row.type}</td>
                          <td>{row.difficulty}</td>
                          <td>{row.valid ? "Yes" : "No"}</td>
                          <td>{row.errors?.join("; ")}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <button type="button" className="primary-button" onClick={() => importCsv().catch((exception) => setCsvError(exception instanceof Error ? exception.message : "Unable to import CSV."))}>
                  Import valid rows
                </button>
              </div>
            ) : null}
            {csvImportSummary ? <p className="notice success">Imported {csvImportSummary.importedRows} row(s), failed {csvImportSummary.failedRows} row(s).</p> : null}
            {csvError ? <p className="notice error">{csvError}</p> : null}
          </section>
        ) : null}

        {activeTab === "score" ? (
          <section className="section">
            <div className="section-header">
              <h2>Scoring sandbox</h2>
              <p>Checks the exact-match scorer for the currently loaded question form.</p>
            </div>
            <div className="dashboard-actions">
              <button type="button" className="secondary-button" onClick={() => scoreQuestion().catch((exception) => setError(exception instanceof Error ? exception.message : "Unable to score question."))}>
                Score current answer
              </button>
            </div>
            {scoreResult ? <p className="notice success">{scoreResult}</p> : null}
          </section>
        ) : null}

        {error ? <p className="notice error">{error}</p> : null}
        {status ? <p className="notice success">{status}</p> : null}
      </section>
    </main>
  );
}
