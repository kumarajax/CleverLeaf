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

type LookupResponse = {
  id: string;
  lookupType: string;
  lookupCode: string;
  lookupMeaning: string;
  sortOrder: number;
  active: boolean;
};

type QuestionOption = {
  key: string;
  text: string;
  correct: boolean;
};

type QuestionAnswer = {
  answerValue: string;
  answerType: string;
  toleranceValue?: number | null;
  caseSensitive?: boolean | null;
};

type QuestionTaxonomyAssignment = {
  taxonomyNodeId: string;
  primary: boolean;
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
  taxonomyAssignments: QuestionTaxonomyAssignment[];
  answers: QuestionAnswer[];
  tags: string[];
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

type PageMetadata = {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type SpringPage<T> = {
  content: T[];
  page?: PageMetadata;
  number?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
};

type CursorPage<T> = {
  content: T[];
  nextCursor: string | null;
  hasNext: boolean;
  size: number;
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
  secondaryTaxonomyNodeIds: string[];
  actor: string;
  questionType: string;
  difficulty: string;
  workflowStatus: string;
  questionText: string;
  explanation: string;
  sourceReference: string;
  licenseCategory: string;
  options: QuestionOption[];
  answersText: string;
  tagsText: string;
};

const taxonomyStatuses = ["ACTIVE", "INACTIVE", "ALL"];
const requestTimeoutMs = 10000;
const taxonomyPageSize = 100;
const allTaxonomyPageSize = 500;
const questionPageSize = 25;
const nodeKeyPattern = /^[A-Z0-9]+(?:_[A-Z0-9]+)*$/;
const taxonomyParentKeys: Record<string, string | null> = {
  CURRICULUM: null,
  EDITION: "CURRICULUM",
  GRADE: "EDITION",
  SUBJECT: "GRADE",
  CHAPTER: "SUBJECT",
  TOPIC: "CHAPTER",
};

function getLovDisplayName(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function getLookupMeaning(lookups: LookupResponse[], value: string) {
  return lookups.find((lookup) => lookup.lookupCode === value)?.lookupMeaning ?? getLovDisplayName(value);
}

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

function trueFalseOptions(): QuestionOption[] {
  return [
    { key: "A", text: "", correct: true },
    { key: "B", text: "", correct: false },
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
  return localStorage.getItem("clearleaf.auth");
}

function removeStoredSession() {
  localStorage.removeItem("clearleaf.auth");
}

function readPage<T>(body: unknown): { content: T[]; page: PageMetadata } {
  if (Array.isArray(body)) {
    return {
      content: body as T[],
      page: {
        number: 0,
        size: body.length,
        totalElements: body.length,
        totalPages: body.length ? 1 : 0,
      },
    };
  }
  const candidate = body as SpringPage<T>;
  const content = Array.isArray(candidate?.content) ? candidate.content : [];
  const page = candidate?.page ?? {
    number: candidate?.number ?? 0,
    size: candidate?.size ?? content.length,
    totalElements: candidate?.totalElements ?? content.length,
    totalPages: candidate?.totalPages ?? (content.length ? 1 : 0),
  };
  return { content, page };
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
  const [questionTypeLookups, setQuestionTypeLookups] = useState<LookupResponse[]>([]);
  const [difficultyLookups, setDifficultyLookups] = useState<LookupResponse[]>([]);
  const [workflowStatusLookups, setWorkflowStatusLookups] = useState<LookupResponse[]>([]);
  const [allNodes, setAllNodes] = useState<TaxonomyNode[]>([]);
  const [taxonomyNodes, setTaxonomyNodes] = useState<TaxonomyNode[]>([]);
  const [taxonomyPage, setTaxonomyPage] = useState<PageMetadata>({ number: 0, size: taxonomyPageSize, totalElements: 0, totalPages: 0 });
  const [taxonomyPageIndex, setTaxonomyPageIndex] = useState(0);
  const [taxonomyFilter, setTaxonomyFilter] = useState("ACTIVE");
  const [selectedTaxonomyNodeId, setSelectedTaxonomyNodeId] = useState("");
  const [taxonomyFormVisible, setTaxonomyFormVisible] = useState(false);
  const [taxonomyForm, setTaxonomyForm] = useState<TaxonomyFormState>({
    id: "",
    levelKey: "CURRICULUM",
    parentId: "",
    nodeKey: "",
    displayName: "",
    sortOrder: 1,
    status: "ACTIVE",
  });
  const [questions, setQuestions] = useState<AdminQuestion[]>([]);
  const [questionCursorStack, setQuestionCursorStack] = useState<(string | null)[]>([null]);
  const [questionCursorIndex, setQuestionCursorIndex] = useState(0);
  const [questionNextCursor, setQuestionNextCursor] = useState<string | null>(null);
  const [questionHasNext, setQuestionHasNext] = useState(false);
  const [questionsLoading, setQuestionsLoading] = useState(false);
  const [questionSearch, setQuestionSearch] = useState("");
  const [questionNodeFilterId, setQuestionNodeFilterId] = useState("");
  const [questionTypeFilter, setQuestionTypeFilter] = useState("");
  const [questionDifficultyFilter, setQuestionDifficultyFilter] = useState("");
  const [questionWorkflowFilter, setQuestionWorkflowFilter] = useState("");
  const [questionForm, setQuestionForm] = useState<QuestionFormState>({
    id: "",
    taxonomyNodeId: "",
    secondaryTaxonomyNodeIds: [],
    actor: "",
    questionType: "SINGLE_SELECT",
    difficulty: "MEDIUM",
    workflowStatus: "DRAFT",
    questionText: "",
    explanation: "",
    sourceReference: "",
    licenseCategory: "CC-BY",
    options: blankOptions(),
    answersText: "",
    tagsText: "",
  });
  const [preview, setPreview] = useState<CsvPreviewResponse | null>(null);
  const [csvObjectKey, setCsvObjectKey] = useState("");
  const [csvImportSummary, setCsvImportSummary] = useState<CsvImportSummary | null>(null);
  const [csvError, setCsvError] = useState("");
  const [activeTab, setActiveTab] = useState<"taxonomy" | "manual" | "csv">("taxonomy");
  const [expandedTaxonomyIds, setExpandedTaxonomyIds] = useState<string[]>([]);
  const [expandedQuestionTaxonomyIds, setExpandedQuestionTaxonomyIds] = useState<string[]>([]);
  const [expandedQuestionIds, setExpandedQuestionIds] = useState<string[]>([]);

  const currentToken = session?.accessToken ?? "";
  const questionTypes = questionTypeLookups.filter((lookup) => lookup.lookupCode !== "ALL").map((lookup) => lookup.lookupCode);
  const difficulties = difficultyLookups.filter((lookup) => lookup.lookupCode !== "ALL").map((lookup) => lookup.lookupCode);
  const workflowStatuses = workflowStatusLookups.filter((lookup) => lookup.lookupCode !== "ALL").map((lookup) => lookup.lookupCode);
  const levelTypeById = useMemo(() => new Map(levelTypes.map((level) => [level.id, level])), [levelTypes]);
  const nodeById = useMemo(() => new Map(allNodes.map((node) => [node.id, node])), [allNodes]);
  const tree = useMemo(() => buildTree(taxonomyNodes), [taxonomyNodes]);
  const expandedTaxonomySet = useMemo(() => new Set(expandedTaxonomyIds), [expandedTaxonomyIds]);
  const expandedQuestionSet = useMemo(() => new Set(expandedQuestionIds), [expandedQuestionIds]);
  const selectedTaxonomyNode = allNodes.find((node) => node.id === selectedTaxonomyNodeId) ?? null;
  const selectedRootTaxonomyNode = selectedTaxonomyNode
    ? getAncestorChain(selectedTaxonomyNode.id)[0] ?? null
    : null;
  const leafNodeIds = useMemo(() => {
    const parentIds = new Set(allNodes.map((node) => node.parentId).filter((id): id is string => Boolean(id)));
    return new Set(allNodes.filter((node) => !parentIds.has(node.id)).map((node) => node.id));
  }, [allNodes]);
  const questionTaxonomyOptions = useMemo(() => {
    const contextNodeId = selectedTaxonomyNodeId || questionForm.taxonomyNodeId;
    const rootId = contextNodeId ? getBranchRootId(contextNodeId) : "";
    return [...allNodes]
      .filter((node) => isActiveTaxonomyBranch(node.id))
      .filter((node) => leafNodeIds.has(node.id))
      .filter((node) => {
        if (!rootId) return true;
        return getBranchRootId(node.id) === rootId;
      })
      .sort((left, right) => left.displayName.localeCompare(right.displayName));
  }, [allNodes, leafNodeIds, questionForm.taxonomyNodeId, selectedTaxonomyNodeId]);
  const questionNodeFilterOptions = useMemo(() => {
    const branchRootId = selectedRootTaxonomyNode?.id ?? "";
    if (!branchRootId) return [];
    return [...allNodes]
      .filter((node) => isActiveTaxonomyBranch(node.id))
      .filter((node) => getBranchRootId(node.id) === branchRootId)
      .sort((left, right) => left.displayName.localeCompare(right.displayName));
  }, [allNodes, selectedRootTaxonomyNode?.id]);
  const filteredQuestions = useMemo(() => {
    const search = questionSearch.trim().toLowerCase();
    return questions.filter((question) => {
      if (!search) return true;
      return [
        question.taxonomyNodeLabel,
        question.questionType,
        question.difficulty,
        question.workflowStatus,
        question.questionText,
      ].some((value) => (value ?? "").toLowerCase().includes(search));
    });
  }, [questionSearch, questions]);
  const groupedQuestions = useMemo(() => {
    const groups = new Map<string, { taxonomyNodeId: string; taxonomyNodeLabel: string; questions: AdminQuestion[] }>();
    filteredQuestions.forEach((question) => {
      const group = groups.get(question.taxonomyNodeId);
      if (group) {
        group.questions.push(question);
      } else {
        groups.set(question.taxonomyNodeId, {
          taxonomyNodeId: question.taxonomyNodeId,
          taxonomyNodeLabel: question.taxonomyNodeLabel,
          questions: [question],
        });
      }
    });
    return [...groups.values()].sort((left, right) => left.taxonomyNodeLabel.localeCompare(right.taxonomyNodeLabel));
  }, [filteredQuestions]);

  function authHeaders(token = currentToken): Record<string, string> {
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  function isAdmin(payloadRoles: string[]) {
    return payloadRoles.includes("administrator");
  }

  async function loadTaxonomy(filter: string, token = currentToken, page = taxonomyPageIndex) {
    const parameters = new URLSearchParams({
      status: filter,
      page: String(page),
      size: String(taxonomyPageSize),
    });
    parameters.append("sort", "sortOrder,asc");
    parameters.append("sort", "displayName,asc");
    const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes?${parameters.toString()}`, {
      headers: authHeaders(token),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    const result = readPage<TaxonomyNode>(body);
    setTaxonomyNodes(result.content);
    setTaxonomyPage(result.page);
    setTaxonomyPageIndex(result.page.number);
    setSelectedTaxonomyNodeId((current) => (result.content.some((node) => node.id === current) ? current : (result.content.length > 0 ? result.content[0].id : "")));
  }

  async function loadAllTaxonomy(token = currentToken) {
    const parameters = new URLSearchParams({
      status: "ALL",
      page: "0",
      size: String(allTaxonomyPageSize),
    });
    parameters.append("sort", "sortOrder,asc");
    parameters.append("sort", "displayName,asc");
    const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes?${parameters.toString()}`, {
      headers: authHeaders(token),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setAllNodes(readPage<TaxonomyNode>(body).content);
  }

  function resetQuestionCursor() {
    setQuestionCursorStack([null]);
    setQuestionCursorIndex(0);
    setQuestionNextCursor(null);
    setQuestionHasNext(false);
  }

  async function loadQuestions(token = currentToken, cursorIndex = questionCursorIndex) {
    setQuestionsLoading(true);
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), requestTimeoutMs);
    try {
      const parameters = new URLSearchParams({
        size: String(questionPageSize),
      });
      const cursor = questionCursorStack[cursorIndex];
      if (cursor) parameters.set("cursor", cursor);
      const taxonomyNodeId = questionNodeFilterId || selectedTaxonomyNodeId;
      if (taxonomyNodeId) {
        parameters.set("taxonomyNodeId", taxonomyNodeId);
        parameters.set("includeDescendants", "true");
      }
      if (questionTypeFilter) parameters.set("questionType", questionTypeFilter);
      if (questionDifficultyFilter) parameters.set("difficulty", questionDifficultyFilter);
      if (questionWorkflowFilter) parameters.set("workflowStatus", questionWorkflowFilter);
      const response = await fetch(`${apiBaseUrl}/api/admin/questions/cursor?${parameters.toString()}`, {
        headers: authHeaders(token),
        signal: controller.signal,
      });
      const body = await response.json().catch(() => []);
      if (!response.ok) {
        throw new Error(body.error || `Request failed with ${response.status}`);
      }
      const result = body as CursorPage<AdminQuestion>;
      setQuestions(result.content);
      setQuestionCursorIndex(cursorIndex);
      setQuestionNextCursor(result.nextCursor);
      setQuestionHasNext(result.hasNext);
      if (result.nextCursor) {
        setQuestionCursorStack((current) => {
          const next = current.slice(0, cursorIndex + 1);
          next[cursorIndex + 1] = result.nextCursor;
          return next;
        });
      } else {
        setQuestionCursorStack((current) => current.slice(0, cursorIndex + 1));
      }
    } finally {
      window.clearTimeout(timeout);
      setQuestionsLoading(false);
    }
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
      const [meResponse, levelResponse, questionTypeResponse, difficultyResponse, workflowStatusResponse] = await Promise.all([
        fetch(`${apiBaseUrl}/api/me`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=TAXONOMY_TYPE&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=QUESTION_TYPE&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=QUESTION_DIFFICULTY&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=QUESTION_WORKFLOW_STATUS&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
      ]);
      const meBody = await meResponse.json().catch(() => ({}));
      const levelBody = await levelResponse.json().catch(() => []);
      const questionTypeBody = await questionTypeResponse.json().catch(() => []);
      const difficultyBody = await difficultyResponse.json().catch(() => []);
      const workflowStatusBody = await workflowStatusResponse.json().catch(() => []);
      if (!meResponse.ok) {
        throw new Error(meBody.error || `Request failed with ${meResponse.status}`);
      }
      if (!levelResponse.ok) {
        throw new Error(levelBody.error || `Request failed with ${levelResponse.status}`);
      }
      if (!questionTypeResponse.ok) {
        throw new Error(questionTypeBody.error || `Request failed with ${questionTypeResponse.status}`);
      }
      if (!difficultyResponse.ok) {
        throw new Error(difficultyBody.error || `Request failed with ${difficultyResponse.status}`);
      }
      if (!workflowStatusResponse.ok) {
        throw new Error(workflowStatusBody.error || `Request failed with ${workflowStatusResponse.status}`);
      }
      setMe(meBody);
      setRoles(meBody.roles ?? payloadRoles);
      const levelLookups = readPage<LookupResponse>(levelBody).content;
      const questionTypeLookups = readPage<LookupResponse>(questionTypeBody).content;
      const difficultyLookups = readPage<LookupResponse>(difficultyBody).content;
      const workflowStatusLookups = readPage<LookupResponse>(workflowStatusBody).content;
      setLevelTypes(levelLookups.map((lookup) => ({
        id: lookup.id,
        levelKey: lookup.lookupCode,
        displayName: lookup.lookupMeaning,
        allowedParentKey: taxonomyParentKeys[lookup.lookupCode] ?? null,
        sortOrder: lookup.sortOrder,
        active: lookup.active,
      })));
      setQuestionTypeLookups(questionTypeLookups);
      setDifficultyLookups(difficultyLookups);
      setWorkflowStatusLookups(workflowStatusLookups);
      const initialLoads = await Promise.allSettled([
        loadAllTaxonomy(parsed.accessToken),
        loadTaxonomy("ACTIVE", parsed.accessToken),
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

  function signOut() {
    removeStoredSession();
    router.replace("/account");
  }

  useEffect(() => {
    if (!currentToken) return;
    loadTaxonomy(taxonomyFilter).catch((exception) =>
      setError(exception instanceof Error ? exception.message : "Unable to load taxonomy.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taxonomyFilter, taxonomyPageIndex, currentToken]);

  useEffect(() => {
    if (activeTab !== "manual") return;
    if (!currentToken) return;
    loadQuestions(currentToken).catch((exception) =>
      setError(exception instanceof Error ? exception.message : "Unable to load questions.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, currentToken, questionCursorIndex, questionNodeFilterId, selectedTaxonomyNodeId, questionTypeFilter, questionDifficultyFilter, questionWorkflowFilter]);

  useEffect(() => {
    if (!questionForm.taxonomyNodeId && selectedTaxonomyNodeId) {
      setQuestionForm((current) => ({ ...current, taxonomyNodeId: selectedTaxonomyNodeId }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTaxonomyNodeId]);

  useEffect(() => {
    if (!tree.length) return;
    setExpandedTaxonomyIds((current) => (current.length ? current : tree.map((node) => node.id)));
  }, [tree]);

  useEffect(() => {
    if (!groupedQuestions.length) return;
    setExpandedQuestionTaxonomyIds((current) => (
      current.length ? current : groupedQuestions.map((group) => group.taxonomyNodeId)
    ));
  }, [groupedQuestions]);

  function resetTaxonomyForm() {
    setTaxonomyFormVisible(false);
    setTaxonomyForm({
      id: "",
      levelKey: "CURRICULUM",
      parentId: "",
      nodeKey: "",
      displayName: "",
      sortOrder: 1,
      status: "ACTIVE",
    });
  }

  function startRootTaxonomyForm() {
    resetTaxonomyForm();
    setTaxonomyFormVisible(true);
  }

  function startChildTaxonomyForm() {
    if (!selectedTaxonomyNode) return;
    const parentLevelKey = levelTypeById.get(selectedTaxonomyNode.levelTypeId)?.levelKey;
    const childLevel = levelTypes.find((level) => level.allowedParentKey === parentLevelKey);
    if (!childLevel) {
      setStatus("");
      setError(`${selectedTaxonomyNode.displayName} is already a leaf topic and cannot have child nodes.`);
      return;
    }
    setError("");
    setStatus("");
    setTaxonomyFormVisible(true);
    setTaxonomyForm({
      id: "",
      levelKey: childLevel.levelKey,
      parentId: selectedTaxonomyNode.id,
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

  function toggleExpandedQuestionTaxonomy(taxonomyNodeId: string) {
    setExpandedQuestionTaxonomyIds((current) => (
      current.includes(taxonomyNodeId)
        ? current.filter((value) => value !== taxonomyNodeId)
        : [...current, taxonomyNodeId]
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
    setTaxonomyFormVisible(true);
    setSelectedTaxonomyNodeId(node.id);
  }

  function getAncestorChain(nodeId: string) {
    const chain: TaxonomyNode[] = [];
    const visited = new Set<string>();
    let current = nodeById.get(nodeId) ?? null;
    while (current && !visited.has(current.id)) {
      visited.add(current.id);
      chain.unshift(current);
      current = current.parentId ? nodeById.get(current.parentId) ?? null : null;
    }
    return chain;
  }

  function getBranchRootId(contextNodeId: string) {
    return getAncestorChain(contextNodeId)[0]?.id ?? "";
  }

  function isActiveTaxonomyBranch(nodeId: string) {
    const chain = getAncestorChain(nodeId);
    return chain.length > 0 && chain.every((node) => node.status === "ACTIVE");
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
      secondaryTaxonomyNodeIds: [],
      actor: me?.email ?? session?.email ?? "",
      questionType: "SINGLE_SELECT",
      difficulty: "MEDIUM",
      workflowStatus: "DRAFT",
      questionText: "",
      explanation: "",
      sourceReference: "",
      licenseCategory: "CC-BY",
      options: blankOptions(),
      answersText: "",
      tagsText: "",
    });
  }

  function loadQuestionIntoForm(question: AdminQuestion) {
    setQuestionForm({
      id: question.id,
      taxonomyNodeId: question.taxonomyNodeId,
      secondaryTaxonomyNodeIds: question.taxonomyAssignments
        .filter((assignment) => !assignment.primary)
        .map((assignment) => assignment.taxonomyNodeId),
      actor: me?.email ?? session?.email ?? "",
      questionType: question.questionType,
      difficulty: question.difficulty,
      workflowStatus: question.workflowStatus,
      questionText: question.questionText,
      explanation: question.explanation ?? "",
      sourceReference: question.sourceReference ?? "",
      licenseCategory: question.licenseCategory ?? "",
      options: question.options.length ? question.options : blankOptions(),
      answersText: question.answers.map((answer) => answer.answerValue).join("\n"),
      tagsText: question.tags.join(", "),
    });
    setActiveTab("manual");
    setSelectedTaxonomyNodeId(question.taxonomyNodeId);
    setQuestionNodeFilterId(question.taxonomyNodeId);
  }

  useEffect(() => {
    if (activeTab === "manual" && selectedTaxonomyNodeId) {
      resetQuestionCursor();
      setQuestionNodeFilterId(selectedTaxonomyNodeId);
    }
  }, [activeTab, selectedTaxonomyNodeId]);

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

  async function deleteTaxonomyNode(node: TaxonomyNode) {
    if (!window.confirm(`Delete taxonomy node "${node.displayName}"? Only unused nodes can be deleted.`)) return;
    setError("");
    setStatus("");
    try {
      const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes/${node.id}`, {
        method: "DELETE",
        headers: authHeaders(),
      });
      if (!response.ok && response.status !== 204) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.error || body.message || `Request failed with ${response.status}`);
      }
      if (selectedTaxonomyNodeId === node.id || taxonomyForm.id === node.id) {
        setSelectedTaxonomyNodeId("");
        resetTaxonomyForm();
      }
      setExpandedTaxonomyIds((current) => current.filter((id) => id !== node.id));
      setStatus("Taxonomy node deleted.");
      await Promise.all([loadAllTaxonomy(), loadTaxonomy(taxonomyFilter)]);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to delete taxonomy node.");
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
          <div className={selectedTaxonomyNodeId === node.id ? "taxonomy-row active" : "taxonomy-row"}>
            <span className="taxonomy-label">
              <button
                type="button"
                className="taxonomy-select-button"
                onClick={() => loadTaxonomyIntoForm(node)}
              >
                <strong>{node.displayName}</strong>
              </button>
              <button
                type="button"
                className="taxonomy-delete-button"
                aria-label={`Delete ${node.displayName}`}
                title={`Delete ${node.displayName}`}
                onClick={(event) => {
                  event.stopPropagation();
                  deleteTaxonomyNode(node);
                }}
              >
                −
              </button>
            </span>
            <span className="taxonomy-meta">{node.status}</span>
          </div>
        </div>,
        ...children,
      ];
    });
  }

  async function submitTaxonomy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setStatus("");
    if (!nodeKeyPattern.test(taxonomyForm.nodeKey)) {
      setError("Node key must contain only uppercase letters, numbers, and single underscores between words.");
      return;
    }
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

  async function submitQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setStatus("");
    try {
      const usesOptions = ["SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE"].includes(questionForm.questionType);
      const answers = questionForm.answersText
        .split("\n")
        .map((answer) => answer.trim())
        .filter(Boolean)
        .map((answerValue) => ({
          answerValue,
          answerType: "EXACT_TEXT",
          caseSensitive: false,
        }));
      const tags = questionForm.tagsText
        .split(",")
        .map((tag) => tag.trim())
        .filter(Boolean);
      const payload = {
        actor: questionForm.actor,
        taxonomyAssignments: [
          {
            taxonomyNodeId: questionForm.taxonomyNodeId,
            primary: true,
          },
          ...questionForm.secondaryTaxonomyNodeIds
            .filter((taxonomyNodeId) => taxonomyNodeId !== questionForm.taxonomyNodeId)
            .map((taxonomyNodeId) => ({ taxonomyNodeId, primary: false })),
        ],
        answers,
        tags,
        question: {
          type: questionForm.questionType,
          difficulty: questionForm.difficulty,
          workflowStatus: questionForm.workflowStatus,
          questionText: questionForm.questionText,
          explanation: questionForm.explanation || null,
          sourceReference: questionForm.sourceReference || null,
          licenseCategory: questionForm.licenseCategory || null,
          options: usesOptions ? questionForm.options : [],
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
  const visibleParentOptions = !taxonomyForm.id && taxonomyForm.parentId
    ? allNodes.filter((node) => node.id === taxonomyForm.parentId)
    : parentOptions;
  const selectedLevelKey = selectedTaxonomyNode ? levelTypeById.get(selectedTaxonomyNode.levelTypeId)?.levelKey : null;
  const nextChildLevel = levelTypes.find((level) => level.allowedParentKey === selectedLevelKey) ?? null;
  const createFormTitle = taxonomyForm.parentId ? "Create child node" : "Create root taxonomy";

  return (
    <main className="admin-shell">
      <section className="admin-panel">
        <div className="admin-topbar">
          <div className="admin-topbar-actions">
            <a className="secondary-button compact-button admin-action-button" href="/dashboard">Dashboard</a>
            <button type="button" className="secondary-button compact-button admin-action-button" onClick={signOut}>Log out</button>
          </div>
        </div>
        <div className="account-tabs">
          <button type="button" className={activeTab === "taxonomy" ? "tab active" : "tab"} onClick={() => setActiveTab("taxonomy")}>Taxonomy</button>
          <button type="button" className={activeTab === "manual" ? "tab active" : "tab"} onClick={() => setActiveTab("manual")}>Manual question</button>
          <button type="button" className={activeTab === "csv" ? "tab active" : "tab"} onClick={() => setActiveTab("csv")}>CSV import</button>
        </div>

        <div className="admin-taxonomy-context">
          <strong>Root taxonomy:</strong>
          <span>{selectedRootTaxonomyNode?.displayName ?? "None selected"}</span>
          <span className="admin-taxonomy-context-separator">|</span>
          <strong>Selected node:</strong>
          <span>{selectedTaxonomyNode?.displayName ?? "None selected"}</span>
        </div>

        {activeTab === "taxonomy" ? (
          <section className="section">
            <div className="section-header">
              <h2>Taxonomy explorer</h2>
              <p>Create a root taxonomy, or select an existing node and add its next valid child level.</p>
            </div>
            <div className="dashboard-actions">
              <label className="inline-select">
                Filter
                <select value={taxonomyFilter} onChange={(event) => {
                  setTaxonomyPageIndex(0);
                  setTaxonomyFilter(event.target.value);
                }}>
                  {taxonomyStatuses.map((value) => (
                    <option key={value} value={value}>{value}</option>
                  ))}
                </select>
              </label>
              <button type="button" className="primary-button compact-button admin-action-button" onClick={startRootTaxonomyForm}>New root taxonomy</button>
              <button
                type="button"
                className="secondary-button compact-button admin-action-button"
                disabled={!selectedTaxonomyNode || !nextChildLevel}
                title={selectedTaxonomyNode && !nextChildLevel ? "Topic nodes cannot have children" : "Create a child under the selected node"}
                onClick={startChildTaxonomyForm}
              >
                Add child node
              </button>
            </div>
            <div className="taxonomy-grid">
              <div className="taxonomy-tree-panel">
                <div className="taxonomy-tree">
                  {renderTaxonomyTree(tree)}
                </div>
                <div className="pagination-bar">
                  <span>
                    Page {taxonomyPage.totalPages ? taxonomyPage.number + 1 : 0} of {taxonomyPage.totalPages}
                    {" "}({taxonomyPage.totalElements} node{taxonomyPage.totalElements === 1 ? "" : "s"})
                  </span>
                  <div className="pagination-actions">
                    <button
                      type="button"
                      className="secondary-button compact-button"
                      disabled={taxonomyPage.number <= 0}
                      onClick={() => setTaxonomyPageIndex((current) => Math.max(0, current - 1))}
                    >
                      Previous
                    </button>
                    <button
                      type="button"
                      className="secondary-button compact-button"
                      disabled={taxonomyPage.totalPages === 0 || taxonomyPage.number >= taxonomyPage.totalPages - 1}
                      onClick={() => setTaxonomyPageIndex((current) => current + 1)}
                    >
                      Next
                    </button>
                  </div>
                </div>
              </div>
              {taxonomyFormVisible ? (
              <div className="card">
                <h3>{taxonomyForm.id ? "Edit taxonomy node" : createFormTitle}</h3>
                <form className="account-form" onSubmit={submitTaxonomy}>
                  <div className="form-grid">
                    <label>
                      Level
                      <select value={taxonomyForm.levelKey} disabled={!taxonomyForm.id} onChange={(event) => setTaxonomyForm((current) => ({ ...current, levelKey: event.target.value, parentId: getDefaultParentId(event.target.value, selectedTaxonomyNodeId) }))}>
                        {levelTypes.map((level) => (
                          <option key={level.id} value={level.levelKey}>{level.displayName}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Status
                      <select value={taxonomyForm.status} disabled={!taxonomyForm.id} onChange={(event) => setTaxonomyForm((current) => ({ ...current, status: event.target.value }))}>
                        {["ACTIVE", "INACTIVE", "DRAFT", "RETIRED", "ARCHIVED"].map((value) => (
                          <option key={value} value={value}>{value}</option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <div className="form-grid">
                    <label>
                      Parent
                      <select value={taxonomyForm.parentId} onChange={(event) => setTaxonomyForm((current) => ({ ...current, parentId: event.target.value }))} disabled={!taxonomyForm.id || !allowedParentKey || visibleParentOptions.length === 0}>
                        <option value="">No parent</option>
                        {visibleParentOptions.map((node) => (
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
                    <input
                      value={taxonomyForm.nodeKey}
                      pattern="[A-Z0-9]+(?:_[A-Z0-9]+)*"
                      title="Use uppercase letters, numbers, and single underscores between words. Example: 2026_EDITION"
                      placeholder="Example: 2026_EDITION"
                      required
                      onChange={(event) => setTaxonomyForm((current) => ({ ...current, nodeKey: event.target.value }))}
                    />
                  </label>
                  <label>
                    Display name
                    <input value={taxonomyForm.displayName} onChange={(event) => setTaxonomyForm((current) => ({ ...current, displayName: event.target.value }))} />
                  </label>
                  <div className="dashboard-actions">
                    <button type="submit" className="primary-button compact-button admin-action-button">{taxonomyForm.id ? "Save" : taxonomyForm.parentId ? "Create child" : "Create root"}</button>
                    <button type="button" className="secondary-button compact-button" onClick={resetTaxonomyForm}>Cancel</button>
                  </div>
                </form>
              </div>
              ) : null}
            </div>
          </section>
        ) : null}

        {activeTab === "manual" ? (
          <section className="section">
            <div className="section-header">
              <h2>Question bank</h2>
              <p>See questions for the selected branch, or narrow them further with the node filter.</p>
            </div>
            <div className="dashboard-actions">
              <label className="inline-select">
                Search
                <input value={questionSearch} onChange={(event) => setQuestionSearch(event.target.value)} placeholder="Search questions" />
              </label>
              <label className="inline-select">
                Node filter
                <select value={questionNodeFilterId || selectedTaxonomyNodeId || ""} onChange={(event) => {
                  resetQuestionCursor();
                  setQuestionNodeFilterId(event.target.value);
                  setQuestionSearch("");
                }}>
                  <option value="">Selected branch</option>
                  {questionNodeFilterOptions.map((node) => (
                    <option key={node.id} value={node.id}>
                      {node.displayName}
                    </option>
                  ))}
                </select>
              </label>
              <label className="inline-select">
                Type
                <select value={questionTypeFilter} onChange={(event) => {
                  resetQuestionCursor();
                  setQuestionTypeFilter(event.target.value);
                }}>
                  {questionTypeLookups.map((lookup) => <option key={lookup.id} value={lookup.lookupCode === "ALL" ? "" : lookup.lookupCode}>{lookup.lookupMeaning}</option>)}
                </select>
              </label>
              <label className="inline-select">
                Difficulty
                <select value={questionDifficultyFilter} onChange={(event) => {
                  resetQuestionCursor();
                  setQuestionDifficultyFilter(event.target.value);
                }}>
                  {difficultyLookups.map((lookup) => <option key={lookup.id} value={lookup.lookupCode === "ALL" ? "" : lookup.lookupCode}>{lookup.lookupMeaning}</option>)}
                </select>
              </label>
              <label className="inline-select">
                Workflow
                <select value={questionWorkflowFilter} onChange={(event) => {
                  resetQuestionCursor();
                  setQuestionWorkflowFilter(event.target.value);
                }}>
                  {workflowStatusLookups.map((lookup) => <option key={lookup.id} value={lookup.lookupCode === "ALL" ? "" : lookup.lookupCode}>{lookup.lookupMeaning}</option>)}
                </select>
              </label>
              <button type="button" className="primary-button compact-button" onClick={() => resetQuestionForm(selectedTaxonomyNodeId)}>New question</button>
            </div>
            <div className="split-layout">
              <div className="card table-card">
                <h3>Questions in scope</h3>
                {questionsLoading ? <p className="muted">Loading questions...</p> : null}
                <div className="pagination-bar">
                  <span>
                    Batch {questionCursorIndex + 1}
                    {" "}({questions.length} question{questions.length === 1 ? "" : "s"})
                  </span>
                  <div className="pagination-actions">
                    <button
                      type="button"
                      className="secondary-button compact-button"
                      disabled={questionCursorIndex <= 0 || questionsLoading}
                      onClick={() => setQuestionCursorIndex((current) => Math.max(0, current - 1))}
                    >
                      Previous
                    </button>
                    <button
                      type="button"
                      className="secondary-button compact-button"
                      disabled={!questionHasNext || !questionNextCursor || questionsLoading}
                      onClick={() => setQuestionCursorIndex((current) => current + 1)}
                    >
                      Next
                    </button>
                  </div>
                </div>
                <div className="question-list">
                  {groupedQuestions.map((group) => {
                    const groupExpanded = expandedQuestionTaxonomyIds.includes(group.taxonomyNodeId);
                    return (
                      <div className="question-taxonomy-group" key={group.taxonomyNodeId}>
                        <button
                          type="button"
                          className="question-taxonomy-group-header"
                          aria-label={groupExpanded ? `Collapse ${group.taxonomyNodeLabel}` : `Expand ${group.taxonomyNodeLabel}`}
                          onClick={() => toggleExpandedQuestionTaxonomy(group.taxonomyNodeId)}
                        >
                          <span>{groupExpanded ? "▾" : "▸"}</span>
                          <strong>{group.taxonomyNodeLabel}</strong>
                          <small>{group.questions.length} question{group.questions.length === 1 ? "" : "s"}</small>
                        </button>
                        {groupExpanded ? group.questions.map((question) => {
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
                                  <span>{getLookupMeaning(workflowStatusLookups, question.workflowStatus)}</span>
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
                                  <div><strong>Type</strong><span>{getLookupMeaning(questionTypeLookups, question.questionType)}</span></div>
                                  <div><strong>Difficulty</strong><span>{getLookupMeaning(difficultyLookups, question.difficulty)}</span></div>
                                  <div><strong>Status</strong><span>{getLookupMeaning(workflowStatusLookups, question.workflowStatus)}</span></div>
                                  <div><strong>Taxonomy</strong><span>{question.taxonomyNodeLabel}</span></div>
                                </div>
                              </div>
                            ) : null}
                          </div>
                          );
                        }) : null}
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
                      Primary taxonomy leaf
                      <select value={questionForm.taxonomyNodeId} onChange={(event) => setQuestionForm((current) => ({
                        ...current,
                        taxonomyNodeId: event.target.value,
                        secondaryTaxonomyNodeIds: [],
                      }))}>
                        {questionTaxonomyOptions.map((node) => (
                          <option key={node.id} value={node.id}>{node.displayName}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Actor
                      <input value={questionForm.actor} onChange={(event) => setQuestionForm((current) => ({ ...current, actor: event.target.value }))} />
                    </label>
                  </div>
                  <label>
                    Secondary taxonomy leaves
                    <select
                      multiple
                      value={questionForm.secondaryTaxonomyNodeIds}
                      onChange={(event) => setQuestionForm((current) => ({
                        ...current,
                        secondaryTaxonomyNodeIds: Array.from(event.target.selectedOptions, (option) => option.value),
                      }))}
                    >
                      {questionTaxonomyOptions
                        .filter((node) => node.id !== questionForm.taxonomyNodeId)
                        .map((node) => (
                          <option key={node.id} value={node.id}>{node.displayName}</option>
                        ))}
                    </select>
                  </label>
                  <div className="form-grid">
                    <label>
                      Question type
                      <select
                        value={questionForm.questionType}
                        onChange={(event) => setQuestionForm((current) => {
                          const nextType = event.target.value;
                          const nextOptions = nextType === "TRUE_FALSE"
                            ? trueFalseOptions()
                            : current.options.length ? current.options : blankOptions();
                          return {
                            ...current,
                            questionType: nextType,
                            options: nextOptions,
                          };
                        })}
                      >
                        {questionTypeLookups.filter((lookup) => lookup.lookupCode !== "ALL").map((lookup) => <option key={lookup.id} value={lookup.lookupCode}>{lookup.lookupMeaning}</option>)}
                      </select>
                    </label>
                    <label>
                      Difficulty
                      <select value={questionForm.difficulty} onChange={(event) => setQuestionForm((current) => ({ ...current, difficulty: event.target.value }))}>
                        {difficultyLookups.filter((lookup) => lookup.lookupCode !== "ALL").map((lookup) => <option key={lookup.id} value={lookup.lookupCode}>{lookup.lookupMeaning}</option>)}
                      </select>
                    </label>
                  </div>
                  <label>
                    Workflow status
                    <select value={questionForm.workflowStatus} onChange={(event) => setQuestionForm((current) => ({ ...current, workflowStatus: event.target.value }))}>
                      {workflowStatusLookups.filter((lookup) => lookup.lookupCode !== "ALL").map((lookup) => <option key={lookup.id} value={lookup.lookupCode}>{lookup.lookupMeaning}</option>)}
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
                  {["SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE"].includes(questionForm.questionType) ? (
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
                  ) : (
                    <label>
                      Accepted answers
                      <textarea
                        value={questionForm.answersText}
                        onChange={(event) => setQuestionForm((current) => ({ ...current, answersText: event.target.value }))}
                        placeholder="Enter one accepted answer per line"
                      />
                    </label>
                  )}
                  <label>
                    Tags
                    <input
                      value={questionForm.tagsText}
                      onChange={(event) => setQuestionForm((current) => ({ ...current, tagsText: event.target.value }))}
                      placeholder="FRACTIONS, WORD_PROBLEM"
                    />
                  </label>
                  <div className="dashboard-actions">
                    {["SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE"].includes(questionForm.questionType) ? (
                      <button
                        type="button"
                        className="secondary-button compact-button"
                        disabled={questionForm.questionType === "TRUE_FALSE" && questionForm.options.length >= 2}
                        title={questionForm.questionType === "TRUE_FALSE" ? "True/false questions can only have two options" : "Add another option"}
                        onClick={() => setQuestionForm((current) => ({ ...current, options: [...current.options, { key: String.fromCharCode(65 + current.options.length), text: "", correct: false }] }))}
                      >
                        Add option
                      </button>
                    ) : null}
                    <button type="submit" className="primary-button compact-button">{questionForm.id ? "Save" : "Create"}</button>
                    <button type="button" className="secondary-button compact-button" onClick={() => resetQuestionForm(questionForm.taxonomyNodeId)}>Reset</button>
                    {questionForm.id ? (
                      <button type="button" className="secondary-button compact-button" onClick={() => deleteQuestion(questionForm.id)}>
                        Delete question
                      </button>
                    ) : null}
                  </div>
                </form>
                <div className="dashboard-actions">
                  {questionForm.id ? <button type="button" className="secondary-button compact-button" onClick={() => resetQuestionForm(questionForm.taxonomyNodeId)}>New question</button> : null}
                </div>
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
              <button type="button" className="secondary-button compact-button" onClick={downloadCsvTemplate}>Template</button>
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
                <button type="button" className="primary-button compact-button" onClick={() => importCsv().catch((exception) => setCsvError(exception instanceof Error ? exception.message : "Unable to import CSV."))}>
                  Import valid rows
                </button>
              </div>
            ) : null}
            {csvImportSummary ? <p className="notice success">Imported {csvImportSummary.importedRows} row(s), failed {csvImportSummary.failedRows} row(s).</p> : null}
            {csvError ? <p className="notice error">{csvError}</p> : null}
          </section>
        ) : null}

        {error ? <p className="notice error">{error}</p> : null}
        {status ? <p className="notice success">{status}</p> : null}
      </section>
    </main>
  );
}
