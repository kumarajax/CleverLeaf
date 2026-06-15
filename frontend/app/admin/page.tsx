"use client";

import { FormEvent, Fragment, ReactNode, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useApplicationConfig } from "../useApplicationConfig";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";
const defaultTenantId = "00000000-0000-0000-0000-000000000100";

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
  tenantMemberships?: TenantMembership[];
};

type TenantMembership = {
  tenantId: string;
  tenantName: string;
  role: string;
  status: string;
};

type TaxonomyNode = {
  id: string;
  levelTypeId: string;
  parentId: string | null;
  externalKey?: string | null;
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

type AdminAssignedTestSummary = {
  testId: string;
  versionId: string;
  publicKey: string;
  name: string;
  status: string;
  questionCount: number;
  timeAllowedSeconds: number;
  availableFrom?: string | null;
  availableUntil?: string | null;
  resultsPublishedAt?: string | null;
  assignedCount: number;
  submittedCount: number;
  createdAt: string;
};

type AdminAssignedTestResult = {
  assignmentId: string;
  attemptId?: string | null;
  studentSubject: string;
  status: string;
  assignedAt: string;
  startedAt?: string | null;
  submittedAt?: string | null;
  resultsPublishedAt?: string | null;
  scorePoints?: number | null;
  maxPoints: number;
  attempt?: AdminResultAttempt | null;
};

type AssignedTestImportJob = {
  jobId: string;
  objectKey: string;
  status: string;
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  failedRows: number;
  errorMessage?: string | null;
  createdAt: string;
  completedAt?: string | null;
};

type AssignedTestImportRow = {
  lineNumber: number;
  testPublicKey: string;
  studentSubject: string;
  status: string;
  message: string;
};

type AdminResultQuestion = {
  attemptQuestionId: string;
  questionNumber: number;
  questionType: string;
  questionText: string;
  options: QuestionOption[];
  selectedOptionKeys: string[];
  answerText?: string | null;
  correctOptionKeys: string[];
  correctAnswerText?: string | null;
  correct?: boolean | null;
};

type AdminResultAttempt = {
  questions: AdminResultQuestion[];
};

type BulkImportColumn = {
  name: string;
  required: boolean;
  description: string;
};

type BulkImportStepMetadata = {
  sequence: number;
  stepCode: string;
  label: string;
  columns: BulkImportColumn[];
};

type BulkImportRow = {
  lineNumber: number;
  values: Record<string, string>;
  errors: string[];
  warnings?: string[];
  valid: boolean;
};

type BulkImportPreviewResponse = {
  objectKey: string;
  stepCode: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  rows: BulkImportRow[];
};

type BulkImportSummary = {
  objectKey: string;
  stepCode: string;
  totalRows: number;
  importedRows: number;
  failedRows: number;
  rows: BulkImportRow[];
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
  totalElements?: number;
};

type QuestionCardPageState = {
  cursorStack: (string | null)[];
  cursorIndex: number;
  nextCursor: string | null;
  hasNext: boolean;
  totalElements: number;
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
const assignedTestWorkflowStatuses = ["ACTIVE", "APPROVED", "PRACTICE"];
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

function formatDateTime(value?: string | null) {
  if (!value) return "Not set";
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function displayAdminTestStatus(test: AdminAssignedTestSummary) {
  const now = Date.now();
  const availableUntil = test.availableUntil ? new Date(test.availableUntil).getTime() : null;
  if (test.assignedCount > 0 && test.submittedCount >= test.assignedCount) return "COMPLETED";
  if (availableUntil && availableUntil < now && test.status !== "DRAFT") return "EXPIRED";
  return test.status;
}

function availabilityLabel(test: AdminAssignedTestSummary) {
  const now = Date.now();
  const from = test.availableFrom ? new Date(test.availableFrom).getTime() : null;
  const until = test.availableUntil ? new Date(test.availableUntil).getTime() : null;
  if (from && from > now) return "Upcoming";
  if (until && until < now) return "Expired";
  return "Open";
}

function usesQuestionOptions(questionType: string) {
  return ["SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE"].includes(questionType);
}

function resultOptionText(question: AdminResultQuestion, keys: string[]) {
  if (!keys.length) return "No answer";
  return keys
    .map((key) => {
      const option = question.options.find((item) => item.key === key);
      return option ? `${option.key}. ${option.text}` : key;
    })
    .join(", ");
}

function resultSubmittedAnswerText(question: AdminResultQuestion) {
  return usesQuestionOptions(question.questionType)
    ? resultOptionText(question, question.selectedOptionKeys ?? [])
    : question.answerText || "No answer";
}

function resultCorrectAnswerText(question: AdminResultQuestion) {
  return usesQuestionOptions(question.questionType)
    ? resultOptionText(question, question.correctOptionKeys ?? [])
    : question.correctAnswerText || "Not configured";
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

type AdminConsoleProps = {
  embedded?: boolean;
};

export function AdminConsole({ embedded = false }: AdminConsoleProps) {
  const router = useRouter();
  const { applicationName } = useApplicationConfig();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [session, setSession] = useState<Session | null>(null);
  const [me, setMe] = useState<MeResponse | null>(null);
  const [roles, setRoles] = useState<string[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState("");
  const [tenantResolved, setTenantResolved] = useState(false);
  const [levelTypes, setLevelTypes] = useState<TaxonomyLevelType[]>([]);
  const [questionTypeLookups, setQuestionTypeLookups] = useState<LookupResponse[]>([]);
  const [difficultyLookups, setDifficultyLookups] = useState<LookupResponse[]>([]);
  const [workflowStatusLookups, setWorkflowStatusLookups] = useState<LookupResponse[]>([]);
  const [adminTestStatusLookups, setAdminTestStatusLookups] = useState<LookupResponse[]>([]);
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
  const [questionCardPages, setQuestionCardPages] = useState<Record<string, QuestionCardPageState>>({});
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
  const [questionFormVisible, setQuestionFormVisible] = useState(false);
  const [importMetadata, setImportMetadata] = useState<BulkImportStepMetadata[]>([]);
  const [activeImportStep, setActiveImportStep] = useState("TAXONOMIES");
  const [selectedImportFiles, setSelectedImportFiles] = useState<Record<string, File | null>>({});
  const [importObjectKeys, setImportObjectKeys] = useState<Record<string, string>>({});
  const [importPreviews, setImportPreviews] = useState<Record<string, BulkImportPreviewResponse>>({});
  const [importSummaries, setImportSummaries] = useState<Record<string, BulkImportSummary>>({});
  const [csvError, setCsvError] = useState("");
  const [activeTab, setActiveTab] = useState<"taxonomy" | "manual" | "import" | "tests">("taxonomy");
  const [testTab, setTestTab] = useState<"history" | "create" | "assign" | "results">("history");
  const [importTab, setImportTab] = useState<"csv" | "json">("csv");
  const [expandedTaxonomyIds, setExpandedTaxonomyIds] = useState<string[]>([]);
  const [expandedQuestionTaxonomyIds, setExpandedQuestionTaxonomyIds] = useState<string[]>([]);
  const [expandedQuestionIds, setExpandedQuestionIds] = useState<string[]>([]);
  const [assignedTests, setAssignedTests] = useState<AdminAssignedTestSummary[]>([]);
  const [assignedTestResults, setAssignedTestResults] = useState<AdminAssignedTestResult[]>([]);
  const [assignedTestResultDetails, setAssignedTestResultDetails] = useState<Record<string, AdminAssignedTestResult>>({});
  const [expandedAssignedResultId, setExpandedAssignedResultId] = useState("");
  const [assignedTestRows, setAssignedTestRows] = useState<AssignedTestImportRow[]>([]);
  const [selectedAssignedTestVersionId, setSelectedAssignedTestVersionId] = useState("");
  const [assignedTestImportFile, setAssignedTestImportFile] = useState<File | null>(null);
  const [assignedTestImportJob, setAssignedTestImportJob] = useState<AssignedTestImportJob | null>(null);
  const [creatingAssignedTest, setCreatingAssignedTest] = useState(false);
  const [assignedTestError, setAssignedTestError] = useState("");
  const [testQuestionResults, setTestQuestionResults] = useState<AdminQuestion[]>([]);
  const [testQuestionLoading, setTestQuestionLoading] = useState(false);
  const [testQuestionCursorStack, setTestQuestionCursorStack] = useState<(string | null)[]>([null]);
  const [testQuestionCursorIndex, setTestQuestionCursorIndex] = useState(0);
  const [testQuestionNextCursor, setTestQuestionNextCursor] = useState<string | null>(null);
  const [testQuestionHasNext, setTestQuestionHasNext] = useState(false);
  const [manualAssignmentStudentSubject, setManualAssignmentStudentSubject] = useState("");
  const [assignedTestForm, setAssignedTestForm] = useState({
    publicKey: "",
    name: "",
    timeAllowedMinutes: 30,
    availableFrom: "",
    availableUntil: "",
  });
  const [selectedAssignedQuestionIds, setSelectedAssignedQuestionIds] = useState<string[]>([]);

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
  const questionCardNodes = useMemo(() => {
    const scopeNodeId = questionNodeFilterId || selectedTaxonomyNodeId;
    if (!scopeNodeId) return [];
    const scopeNode = nodeById.get(scopeNodeId);
    if (!scopeNode) return [];
    const scopedLeaves = allNodes
      .filter((node) => isActiveTaxonomyBranch(node.id))
      .filter((node) => leafNodeIds.has(node.id))
      .filter((node) => getAncestorChain(node.id).some((ancestor) => ancestor.id === scopeNodeId));
    return (scopedLeaves.length ? scopedLeaves : [scopeNode])
      .sort((left, right) => left.displayName.localeCompare(right.displayName));
  }, [allNodes, leafNodeIds, nodeById, questionNodeFilterId, selectedTaxonomyNodeId]);
  const groupedQuestionsByCard = useMemo(() => {
    const byNode = new Map(groupedQuestions.map((group) => [group.taxonomyNodeId, group.questions]));
    return questionCardNodes.map((node) => ({
      taxonomyNodeId: node.id,
      taxonomyNodeLabel: node.displayName,
      questions: byNode.get(node.id) ?? [],
    }));
  }, [groupedQuestions, questionCardNodes]);
  const selectedAssignedQuestions = useMemo(() => {
    const byId = new Map([...questions, ...testQuestionResults].map((question) => [question.id, question]));
    return selectedAssignedQuestionIds
      .map((id) => byId.get(id))
      .filter((question): question is AdminQuestion => Boolean(question));
  }, [questions, testQuestionResults, selectedAssignedQuestionIds]);
  const submittedAssignedTestResults = useMemo(() => assignedTestResults.filter((result) => result.status === "SUBMITTED"), [assignedTestResults]);
  const adminTestStatusMeaningByCode = useMemo(() => new Map(adminTestStatusLookups.map((lookup) => [lookup.lookupCode, lookup.lookupMeaning])), [adminTestStatusLookups]);
  const assignableAssignedTests = useMemo(() => assignedTests.filter((test) => test.status === "ACTIVE" || test.status === "PUBLISHED"), [assignedTests]);
  const resultEligibleAssignedTests = useMemo(() => assignedTests.filter((test) => ["PUBLISHED", "COMPLETED", "EXPIRED"].includes(displayAdminTestStatus(test))), [assignedTests]);

  function authHeaders(token = currentToken, tenantId = selectedTenantId): Record<string, string> {
    const resolvedTenantId = tenantId || defaultTenantId;
    return token ? { Authorization: `Bearer ${token}`, "X-CleverLeaf-Tenant-Id": resolvedTenantId } : { "X-CleverLeaf-Tenant-Id": resolvedTenantId };
  }

  function adminTenantId(profile: MeResponse, payloadRoles: string[]) {
    const adminMembership = (profile.tenantMemberships ?? [])
      .find((membership) => membership.role === "ADMIN" && membership.status === "ACTIVE");
    if (adminMembership) return adminMembership.tenantId;
    return payloadRoles.includes("administrator") ? defaultTenantId : "";
  }

  async function loadTaxonomy(filter: string, token = currentToken, page = taxonomyPageIndex, tenantId = selectedTenantId) {
    const parameters = new URLSearchParams({
      status: filter,
      page: String(page),
      size: String(taxonomyPageSize),
    });
    parameters.append("sort", "sortOrder,asc");
    parameters.append("sort", "displayName,asc");
    const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes?${parameters.toString()}`, {
      headers: authHeaders(token, tenantId),
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

  async function loadAllTaxonomy(token = currentToken, tenantId = selectedTenantId) {
    const parameters = new URLSearchParams({
      status: "ALL",
      page: "0",
      size: String(allTaxonomyPageSize),
    });
    parameters.append("sort", "sortOrder,asc");
    parameters.append("sort", "displayName,asc");
    const response = await fetch(`${apiBaseUrl}/api/admin/taxonomy/nodes?${parameters.toString()}`, {
      headers: authHeaders(token, tenantId),
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
    setQuestionCardPages({});
    setExpandedQuestionTaxonomyIds([]);
    setExpandedQuestionIds([]);
  }

  async function loadQuestions(token = currentToken, cardPages = questionCardPages) {
    setQuestionsLoading(true);
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), requestTimeoutMs);
    try {
      const results = await Promise.all(questionCardNodes.map(async (node) => {
        const pageState = cardPages[node.id] ?? { cursorStack: [null], cursorIndex: 0, nextCursor: null, hasNext: false, totalElements: 0 };
        const parameters = new URLSearchParams({
          size: String(questionPageSize),
        });
        const cursor = pageState.cursorStack[pageState.cursorIndex];
        if (cursor) parameters.set("cursor", cursor);
        parameters.set("taxonomyNodeId", node.id);
        parameters.set("includeDescendants", "false");
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
        return { nodeId: node.id, result: body as CursorPage<AdminQuestion>, pageState };
      }));
      const nextPages: Record<string, QuestionCardPageState> = {};
      results.forEach(({ nodeId, result, pageState }) => {
        const cursorStack = result.nextCursor
          ? [...pageState.cursorStack.slice(0, pageState.cursorIndex + 1), result.nextCursor]
          : pageState.cursorStack.slice(0, pageState.cursorIndex + 1);
        nextPages[nodeId] = {
          cursorStack,
          cursorIndex: pageState.cursorIndex,
          nextCursor: result.nextCursor,
          hasNext: result.hasNext,
          totalElements: result.totalElements ?? result.content.length,
        };
      });
      const nextQuestions = results.flatMap(({ result }) => result.content);
      setQuestions(nextQuestions);
      setQuestionCardPages(nextPages);
    } finally {
      window.clearTimeout(timeout);
      setQuestionsLoading(false);
    }
  }

  async function loadAssignedTests(token = currentToken) {
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests`, {
      headers: authHeaders(token),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    const rows = Array.isArray(body) ? body : [];
    setAssignedTests(rows);
    setSelectedAssignedTestVersionId((current) => rows.some((row: AdminAssignedTestSummary) => row.versionId === current) ? current : rows[0]?.versionId || "");
  }

  async function activateAssignedTest(test: AdminAssignedTestSummary) {
    setAssignedTestError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/${test.versionId}/activate`, {
      method: "POST",
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setStatus(`Activated ${test.publicKey}.`);
    await loadAssignedTests();
  }

  async function deleteAssignedTest(test: AdminAssignedTestSummary) {
    setAssignedTestError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/${test.versionId}`, {
      method: "DELETE",
      headers: authHeaders(),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setStatus(`Deleted ${test.publicKey}.`);
    setSelectedAssignedTestVersionId((current) => current === test.versionId ? "" : current);
    await loadAssignedTests();
  }

  function resetTestQuestionCursor() {
    setTestQuestionCursorStack([null]);
    setTestQuestionCursorIndex(0);
    setTestQuestionNextCursor(null);
    setTestQuestionHasNext(false);
  }

  async function loadTestQuestionSearch(token = currentToken, cursorIndex = testQuestionCursorIndex, cursorStack = testQuestionCursorStack) {
    setTestQuestionLoading(true);
    try {
      const parameters = new URLSearchParams({
        size: "50",
        includeDescendants: "true",
      });
      const cursor = cursorStack[cursorIndex];
      if (cursor) parameters.set("cursor", cursor);
      if (questionSearch.trim()) parameters.set("search", questionSearch.trim());
      if (questionNodeFilterId) parameters.set("taxonomyNodeId", questionNodeFilterId);
      if (questionTypeFilter) parameters.set("questionType", questionTypeFilter);
      if (questionDifficultyFilter) parameters.set("difficulty", questionDifficultyFilter);
      assignedTestWorkflowStatuses.forEach((workflowStatus) => parameters.append("workflowStatuses", workflowStatus));
      const response = await fetch(`${apiBaseUrl}/api/admin/questions/cursor?${parameters.toString()}`, {
        headers: authHeaders(token),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || `Request failed with ${response.status}`);
      }
      const result = body as CursorPage<AdminQuestion>;
      const nextStack = result.nextCursor
        ? [...cursorStack.slice(0, cursorIndex + 1), result.nextCursor]
        : cursorStack.slice(0, cursorIndex + 1);
      setTestQuestionResults((result.content ?? []).filter((question) => question.taxonomyNodeStatus === "ACTIVE"));
      setTestQuestionCursorStack(nextStack);
      setTestQuestionCursorIndex(cursorIndex);
      setTestQuestionNextCursor(result.nextCursor);
      setTestQuestionHasNext(result.hasNext);
    } finally {
      setTestQuestionLoading(false);
    }
  }

  function loadTestQuestionPage(cursorIndex: number) {
    const nextIndex = Math.max(0, cursorIndex);
    setTestQuestionCursorIndex(nextIndex);
    loadTestQuestionSearch(currentToken, nextIndex, testQuestionCursorStack).catch((exception) =>
      setAssignedTestError(exception instanceof Error ? exception.message : "Unable to search questions.")
    );
  }

  async function loadAssignedTestResults(versionId = selectedAssignedTestVersionId) {
    if (!versionId) {
      setAssignedTestResults([]);
      return;
    }
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/${versionId}/results`, {
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setAssignedTestResults(Array.isArray(body) ? body : []);
    setAssignedTestResultDetails({});
    setExpandedAssignedResultId("");
  }

  async function loadAssignedTestResultDetail(result: AdminAssignedTestResult) {
    if (!selectedAssignedTestVersionId) return;
    if (expandedAssignedResultId === result.assignmentId) {
      setExpandedAssignedResultId("");
      return;
    }
    setAssignedTestError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/${selectedAssignedTestVersionId}/results/${result.assignmentId}`, {
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setAssignedTestResultDetails((current) => ({ ...current, [result.assignmentId]: body }));
    setExpandedAssignedResultId(result.assignmentId);
  }

  async function createAssignedTest(event: FormEvent) {
    event.preventDefault();
    setAssignedTestError("");
    if (selectedAssignedQuestionIds.length === 0) {
      setAssignedTestError("Select at least one question before creating the test.");
      return;
    }
    setCreatingAssignedTest(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          publicKey: assignedTestForm.publicKey,
          name: assignedTestForm.name,
          timeAllowedSeconds: Math.max(1, assignedTestForm.timeAllowedMinutes) * 60,
          availableFrom: assignedTestForm.availableFrom ? new Date(assignedTestForm.availableFrom).toISOString() : null,
          availableUntil: assignedTestForm.availableUntil ? new Date(assignedTestForm.availableUntil).toISOString() : null,
          questionIds: selectedAssignedQuestionIds,
        }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || `Request failed with ${response.status}`);
      }
      setStatus("Draft test created.");
      setAssignedTestForm({ publicKey: "", name: "", timeAllowedMinutes: 30, availableFrom: "", availableUntil: "" });
      setSelectedAssignedQuestionIds([]);
      await loadAssignedTests();
    } catch (exception) {
      setAssignedTestError(exception instanceof Error ? exception.message : "Unable to create assigned test.");
    } finally {
      setCreatingAssignedTest(false);
    }
  }

  function toggleAssignedQuestion(questionId: string) {
    setSelectedAssignedQuestionIds((current) => (
      current.includes(questionId)
        ? current.filter((id) => id !== questionId)
        : [...current, questionId]
    ));
  }

  async function importAssignedTestAssignments() {
    if (!assignedTestImportFile) return;
    setAssignedTestError("");
    const formData = new FormData();
    formData.append("file", assignedTestImportFile);
    const uploadResponse = await fetch(`${apiBaseUrl}/api/admin/media/upload`, {
      method: "POST",
      headers: authHeaders(),
      body: formData,
    });
    const uploadBody = await uploadResponse.json().catch(() => ({}));
    if (!uploadResponse.ok) {
      throw new Error(uploadBody.error || `Request failed with ${uploadResponse.status}`);
    }
    const importResponse = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/assignment-imports?objectKey=${encodeURIComponent(uploadBody.objectKey)}`, {
      method: "POST",
      headers: authHeaders(),
    });
    const importBody = await importResponse.json().catch(() => ({}));
    if (!importResponse.ok) {
      throw new Error(importBody.error || `Request failed with ${importResponse.status}`);
    }
    setAssignedTestImportJob(importBody);
    setStatus("Assignment import started.");
  }

  async function refreshAssignedTestImportJob() {
    if (!assignedTestImportJob) return;
    const jobResponse = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/assignment-imports/${assignedTestImportJob.jobId}`, {
      headers: authHeaders(),
    });
    const jobBody = await jobResponse.json().catch(() => ({}));
    if (!jobResponse.ok) {
      throw new Error(jobBody.error || `Request failed with ${jobResponse.status}`);
    }
    setAssignedTestImportJob(jobBody);
    const rowsResponse = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/assignment-imports/${assignedTestImportJob.jobId}/rows`, {
      headers: authHeaders(),
    });
    const rowsBody = await rowsResponse.json().catch(() => []);
    if (!rowsResponse.ok) {
      throw new Error(rowsBody.error || `Request failed with ${rowsResponse.status}`);
    }
    setAssignedTestRows(Array.isArray(rowsBody) ? rowsBody : []);
    await loadAssignedTests();
    if (selectedAssignedTestVersionId) await loadAssignedTestResults(selectedAssignedTestVersionId);
  }

  async function assignTestToStudent() {
    if (!selectedAssignedTestVersionId) {
      setAssignedTestError("Select a test before assigning it.");
      return;
    }
    if (!manualAssignmentStudentSubject.trim()) {
      setAssignedTestError("Student subject is required.");
      return;
    }
    setAssignedTestError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/${selectedAssignedTestVersionId}/assignments`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders(),
      },
      body: JSON.stringify({ studentSubject: manualAssignmentStudentSubject.trim() }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setManualAssignmentStudentSubject("");
    setStatus("Test assigned to student.");
    await Promise.all([loadAssignedTests(), loadAssignedTestResults(selectedAssignedTestVersionId)]);
  }

  async function publishAssignedTestResults(versionId = selectedAssignedTestVersionId) {
    if (!versionId) return;
    setAssignedTestError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/${versionId}/publish-results`, {
      method: "POST",
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setStatus("Results published.");
    await Promise.all([loadAssignedTests(), loadAssignedTestResults(versionId)]);
  }

  async function publishAssignedTestStudentResult(result: AdminAssignedTestResult) {
    if (!selectedAssignedTestVersionId) return;
    setAssignedTestError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/assigned-tests/${selectedAssignedTestVersionId}/results/${result.assignmentId}/publish`, {
      method: "POST",
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setStatus(`Published result for ${result.studentSubject}.`);
    await loadAssignedTestResults(selectedAssignedTestVersionId);
  }

  function loadQuestionCardPage(taxonomyNodeId: string, cursorIndex: number) {
    const current = questionCardPages[taxonomyNodeId] ?? { cursorStack: [null], cursorIndex: 0, nextCursor: null, hasNext: false, totalElements: 0 };
    const nextPages = {
      ...questionCardPages,
      [taxonomyNodeId]: {
        ...current,
        cursorIndex: Math.max(0, cursorIndex),
      },
    };
    setQuestionCardPages(nextPages);
    loadQuestions(currentToken, nextPages).catch((exception) =>
      setError(exception instanceof Error ? exception.message : "Unable to load questions.")
    );
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
      setTenantResolved(false);
      const payload = decodePayload(parsed.accessToken);
      const payloadRoles = payload?.realm_access?.roles ?? [];
      setRoles(payloadRoles);
      setQuestionForm((current) => ({ ...current, actor: parsed.email ?? payload?.email ?? current.actor }));
      const [meResponse, levelResponse, questionTypeResponse, difficultyResponse, workflowStatusResponse, adminTestStatusResponse] = await Promise.all([
        fetch(`${apiBaseUrl}/api/me`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=TAXONOMY_TYPE&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=QUESTION_TYPE&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=QUESTION_DIFFICULTY&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=QUESTION_WORKFLOW_STATUS&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
        fetch(`${apiBaseUrl}/api/common/lookups?lookupType=ADMIN_TEST_STATUS&status=ACTIVE`, { headers: { Authorization: `Bearer ${parsed.accessToken}` } }),
      ]);
      const meBody = await meResponse.json().catch(() => ({}));
      const levelBody = await levelResponse.json().catch(() => []);
      const questionTypeBody = await questionTypeResponse.json().catch(() => []);
      const difficultyBody = await difficultyResponse.json().catch(() => []);
      const workflowStatusBody = await workflowStatusResponse.json().catch(() => []);
      const adminTestStatusBody = await adminTestStatusResponse.json().catch(() => []);
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
      if (!adminTestStatusResponse.ok) {
        throw new Error(adminTestStatusBody.error || `Request failed with ${adminTestStatusResponse.status}`);
      }
      setMe(meBody);
      setRoles(meBody.roles ?? payloadRoles);
      const tenantId = adminTenantId(meBody, meBody.roles ?? payloadRoles);
      if (!tenantId) {
        throw new Error("Tenant admin access is required");
      }
      setSelectedTenantId(tenantId);
      setTenantResolved(true);
      const levelLookups = readPage<LookupResponse>(levelBody).content;
      const questionTypeLookups = readPage<LookupResponse>(questionTypeBody).content;
      const difficultyLookups = readPage<LookupResponse>(difficultyBody).content;
      const workflowStatusLookups = readPage<LookupResponse>(workflowStatusBody).content;
      const adminTestStatusLookups = readPage<LookupResponse>(adminTestStatusBody).content;
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
      setAdminTestStatusLookups(adminTestStatusLookups);
      const initialLoads = await Promise.allSettled([
        loadAllTaxonomy(parsed.accessToken, tenantId),
        loadTaxonomy("ACTIVE", parsed.accessToken, taxonomyPageIndex, tenantId),
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
    if (!currentToken || !tenantResolved) return;
    loadTaxonomy(taxonomyFilter).catch((exception) =>
      setError(exception instanceof Error ? exception.message : "Unable to load taxonomy.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taxonomyFilter, taxonomyPageIndex, currentToken, tenantResolved, selectedTenantId]);

  useEffect(() => {
    if (activeTab !== "manual") return;
    if (!currentToken || !tenantResolved) return;
    loadQuestions(currentToken).catch((exception) =>
      setError(exception instanceof Error ? exception.message : "Unable to load questions.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, currentToken, tenantResolved, selectedTenantId, questionCursorIndex, questionNodeFilterId, selectedTaxonomyNodeId, questionTypeFilter, questionDifficultyFilter, questionWorkflowFilter]);

  useEffect(() => {
    if (activeTab !== "tests" || !currentToken || !tenantResolved) return;
    Promise.all([
      loadAssignedTests(currentToken),
      loadTestQuestionSearch(currentToken, testQuestionCursorIndex, testQuestionCursorStack),
    ]).catch((exception) =>
      setAssignedTestError(exception instanceof Error ? exception.message : "Unable to load assigned test data.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, currentToken, tenantResolved, selectedTenantId, questionSearch, questionNodeFilterId, questionTypeFilter, questionDifficultyFilter, questionWorkflowFilter]);

  useEffect(() => {
    if (activeTab !== "tests" || !selectedAssignedTestVersionId || !tenantResolved) return;
    loadAssignedTestResults(selectedAssignedTestVersionId).catch((exception) =>
      setAssignedTestError(exception instanceof Error ? exception.message : "Unable to load assigned test results.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, selectedAssignedTestVersionId, tenantResolved, selectedTenantId]);

  useEffect(() => {
    if (activeTab !== "import" || !currentToken || !tenantResolved || importMetadata.length > 0) return;
    loadImportMetadata().catch((exception) =>
      setCsvError(exception instanceof Error ? exception.message : "Unable to load import metadata.")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, currentToken, tenantResolved, selectedTenantId, importMetadata.length]);

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
    const parentLevelKey = levelTypeById.get(selectedTaxonomyNode.levelTypeId)?.levelKey ?? "TOPIC";
    setError("");
    setStatus("");
    setTaxonomyFormVisible(true);
    setTaxonomyForm({
      id: "",
      levelKey: parentLevelKey,
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

  function getSelectedRootBranchNodes() {
    const rootId = selectedRootTaxonomyNode?.id;
    if (!rootId) return [];
    return allNodes.filter((node) => getBranchRootId(node.id) === rootId);
  }

  function taxonomyImportKey(node: TaxonomyNode) {
    return node.externalKey || node.nodeKey || node.id;
  }

  function csvCell(value: string | number | null | undefined) {
    return `"${String(value ?? "").replaceAll("\"", "\"\"")}"`;
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

  function startNewQuestion(nextNodeId = selectedTaxonomyNodeId) {
    resetQuestionForm(nextNodeId);
    setQuestionFormVisible(true);
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
    setQuestionFormVisible(true);
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
        setQuestionFormVisible(false);
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
      setQuestionFormVisible(false);
      await loadQuestions();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to save question.");
    }
  }

  async function loadImportMetadata() {
    const response = await fetch(`${apiBaseUrl}/api/admin/imports/bulk/metadata`, {
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setImportMetadata(body);
    if (body[0]?.stepCode) {
      setActiveImportStep(body[0].stepCode);
    }
  }

  async function uploadImportCsv(stepCode: string, file: File) {
    setCsvError("");
    setStatus("");
    setImportPreviews((current) => {
      const next = { ...current };
      delete next[stepCode];
      return next;
    });
    setImportSummaries((current) => {
      const next = { ...current };
      delete next[stepCode];
      return next;
    });
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
    setImportObjectKeys((current) => ({ ...current, [stepCode]: uploadBody.objectKey }));
    const previewResponse = await fetch(`${apiBaseUrl}/api/admin/imports/bulk/${stepCode}/preview?objectKey=${encodeURIComponent(uploadBody.objectKey)}`, {
      headers: authHeaders(),
    });
    const previewBody = await previewResponse.json().catch(() => ({}));
    if (!previewResponse.ok) {
      throw new Error(previewBody.error || `Request failed with ${previewResponse.status}`);
    }
    setImportPreviews((current) => ({ ...current, [stepCode]: previewBody }));
  }

  async function importCsvStep(stepCode: string) {
    const objectKey = importObjectKeys[stepCode];
    if (!objectKey) return;
    setCsvError("");
    const response = await fetch(`${apiBaseUrl}/api/admin/imports/bulk/${stepCode}?objectKey=${encodeURIComponent(objectKey)}`, {
      method: "POST",
      headers: authHeaders(),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || `Request failed with ${response.status}`);
    }
    setImportSummaries((current) => ({ ...current, [stepCode]: body }));
    setStatus(`Imported ${body.importedRows} row(s) for ${stepCode}.`);
    if (stepCode === "TAXONOMIES") {
      await Promise.all([loadAllTaxonomy(), loadTaxonomy(taxonomyFilter)]);
    }
    if (stepCode === "QUESTIONS" || stepCode === "QUESTION_OPTIONS" || stepCode === "CORRECT_ANSWERS") {
      resetQuestionCursor();
      await loadQuestions();
    }
  }

  function downloadCsvTemplate(step: BulkImportStepMetadata) {
    const headers = step.columns.map((column) => column.name);
    const rows = templateRows(step.stepCode, headers);
    const blob = new Blob([
      headers.join(",") + "\n" + rows.map((row) => row.join(",")).join("\n") + "\n",
    ], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `clearleaf-${step.stepCode.toLowerCase()}-template.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function templateRows(stepCode: string, headers: string[]) {
    const branchNodes = getSelectedRootBranchNodes();
    const branchLeafNodes = branchNodes
      .filter((node) => leafNodeIds.has(node.id))
      .sort((left, right) => left.displayName.localeCompare(right.displayName));
    const fallbackQuestionKey = "QUESTION_KEY_001";
    if (stepCode === "TAXONOMIES") {
      const rows = branchNodes.length ? branchNodes : [selectedTaxonomyNode].filter((node): node is TaxonomyNode => Boolean(node));
      return rows.map((node) => {
        const levelKey = levelTypeById.get(node.levelTypeId)?.levelKey ?? "";
        const parent = node.parentId ? nodeById.get(node.parentId) : null;
        return headers.map((header) => csvCell({
          PublicKey: taxonomyImportKey(node),
          levelKey,
          ParentPublicKey: parent ? taxonomyImportKey(parent) : "",
          nodeKey: node.nodeKey,
          displayName: node.displayName,
          status: node.status,
          sortOrder: node.sortOrder,
        }[header]));
      });
    }
    if (stepCode === "QUESTIONS") {
      const rows = branchLeafNodes.length ? branchLeafNodes : [selectedTaxonomyNode].filter((node): node is TaxonomyNode => Boolean(node));
      const rootKey = selectedRootTaxonomyNode ? taxonomyImportKey(selectedRootTaxonomyNode) : "";
      return rows.map((node, index) => headers.map((header) => csvCell({
        PublicKey: `QUESTION_KEY_${String(index + 1).padStart(3, "0")}`,
        RootTaxonomy: rootKey,
        ChildTaxonomy: node.nodeKey,
        actor: me?.email ?? session?.email ?? "admin@example.com",
        questionType: "SINGLE_SELECT",
        difficulty: "MEDIUM",
        workflowStatus: "DRAFT",
        questionText: `Enter question for ${node.displayName}`,
        explanation: "",
        sourceReference: "",
        licenseCategory: "CC-BY",
        tags: node.nodeKey,
      }[header])));
    }
    if (stepCode === "QUESTION_OPTIONS") {
      return ["A", "B", "C", "D"].map((optionKey, index) => headers.map((header) => csvCell({
        QuestionPublicKey: fallbackQuestionKey,
        optionKey,
        optionText: `Option ${optionKey}`,
        sortOrder: index + 1,
      }[header])));
    }
    if (stepCode === "CORRECT_ANSWERS") {
      return [headers.map((header) => csvCell({
        QuestionPublicKey: fallbackQuestionKey,
        optionKey: "A",
        answerValue: "",
        answerType: "",
        toleranceValue: "",
        caseSensitive: "",
        sortOrder: "1",
      }[header]))];
    }
    return [headers.map(() => csvCell(""))];
  }

  if (loading) {
    if (embedded) {
      return (
        <section className="admin-panel embedded-admin-panel">
          <div className="eyebrow">{applicationName} Admin</div>
          <h1>Loading admin console</h1>
          <p className="lede">Verifying session and loading taxonomy.</p>
        </section>
      );
    }
    return (
      <main className="account-shell">
        <section className="account-panel">
          <div className="eyebrow">{applicationName} Admin</div>
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
  const createFormTitle = taxonomyForm.parentId ? "Create child node" : "Create root taxonomy";

  return (
    <main className={embedded ? "admin-shell embedded-admin-shell" : "admin-shell"}>
      <section className={embedded ? "admin-panel embedded-admin-panel" : "admin-panel"}>
        {!embedded ? (
        <div className="admin-topbar">
          <a className="secondary-button compact-button admin-action-button" href="/dashboard">Dashboard</a>
          <div className="admin-topbar-actions">
            <button type="button" className="secondary-button compact-button admin-action-button" onClick={signOut}>Log out</button>
          </div>
        </div>
        ) : null}
        <div className="account-tabs admin-primary-tabs" role="tablist" aria-label="Configure sections">
          <button type="button" className={activeTab === "taxonomy" ? "tab active" : "tab"} onClick={() => setActiveTab("taxonomy")}>Taxonomy</button>
          <button type="button" className={activeTab === "manual" ? "tab active" : "tab"} onClick={() => setActiveTab("manual")}>Manual question</button>
          <button type="button" className={activeTab === "tests" ? "tab active" : "tab"} onClick={() => setActiveTab("tests")}>Tests</button>
          <button type="button" className={activeTab === "import" ? "tab active" : "tab"} onClick={() => {
            setActiveTab("import");
            setImportTab("csv");
          }}>Import</button>
        </div>

        {error ? <p className="notice error">{error}</p> : null}
        {status ? <p className="notice success">{status}</p> : null}

        {activeTab !== "tests" ? (
        <div className="admin-taxonomy-context">
          <strong>Root taxonomy:</strong>
          <span>{selectedRootTaxonomyNode?.displayName ?? "None selected"}</span>
          <span className="admin-taxonomy-context-separator">|</span>
          <strong>Selected node:</strong>
          <span>{selectedTaxonomyNode?.displayName ?? "None selected"}</span>
        </div>
        ) : null}

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
                disabled={!selectedTaxonomyNode}
                title="Create a child under the selected node"
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
                      <select value={taxonomyForm.levelKey} onChange={(event) => setTaxonomyForm((current) => ({ ...current, levelKey: event.target.value }))}>
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
              <button type="button" className="primary-button compact-button" onClick={() => startNewQuestion(selectedTaxonomyNodeId)}>New question</button>
            </div>
            <div className="manual-layout">
              <div className="card table-card">
                <h3>Questions in scope</h3>
                {questionsLoading ? <p className="muted">Loading questions...</p> : null}
                <div className="question-list">
                  {groupedQuestionsByCard.map((group) => {
                    const groupExpanded = expandedQuestionTaxonomyIds.includes(group.taxonomyNodeId);
                    const pageState = questionCardPages[group.taxonomyNodeId] ?? { cursorStack: [null], cursorIndex: 0, nextCursor: null, hasNext: false, totalElements: group.questions.length };
                    const totalQuestions = pageState.totalElements;
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
                          <small>{totalQuestions} question{totalQuestions === 1 ? "" : "s"}</small>
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
                        {groupExpanded ? (
                          <div className="question-group-footer">
                            <div className="pagination-bar">
                              <span>
                                Batch {pageState.cursorIndex + 1}
                                {" "}({group.questions.length} shown of {totalQuestions})
                              </span>
                              <div className="pagination-actions">
                                <button
                                  type="button"
                                  className="secondary-button compact-button"
                                  disabled={pageState.cursorIndex <= 0 || questionsLoading}
                                  onClick={() => loadQuestionCardPage(group.taxonomyNodeId, pageState.cursorIndex - 1)}
                                >
                                  Previous
                                </button>
                                <button
                                  type="button"
                                  className="secondary-button compact-button"
                                  disabled={!pageState.hasNext || !pageState.nextCursor || questionsLoading}
                                  onClick={() => loadQuestionCardPage(group.taxonomyNodeId, pageState.cursorIndex + 1)}
                                >
                                  Next
                                </button>
                              </div>
                            </div>
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
            {questionFormVisible ? (
              <div
                className="modal-backdrop"
                role="presentation"
                onMouseDown={(event) => {
                  if (event.target === event.currentTarget) {
                    setQuestionFormVisible(false);
                  }
                }}
              >
                <div className="modal-panel question-modal" role="dialog" aria-modal="true" aria-labelledby="question-form-title">
                  <div className="modal-header">
                    <h3 id="question-form-title">{questionForm.id ? "Edit question" : "Create question"}</h3>
                    <button type="button" className="modal-close-button" aria-label="Close question form" onClick={() => setQuestionFormVisible(false)}>
                      x
                    </button>
                  </div>
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
                    <button type="button" className="secondary-button compact-button" onClick={() => setQuestionFormVisible(false)}>Cancel</button>
                    {questionForm.id ? (
                      <button type="button" className="secondary-button compact-button" onClick={() => deleteQuestion(questionForm.id)}>
                        Delete question
                      </button>
                    ) : null}
                  </div>
                </form>
                <div className="dashboard-actions">
                  {questionForm.id ? <button type="button" className="secondary-button compact-button" onClick={() => startNewQuestion(questionForm.taxonomyNodeId)}>New question</button> : null}
                </div>
              </div>
            </div>
            ) : null}
          </section>
        ) : null}

        {activeTab === "tests" ? (
          <section className="section">
            {assignedTestError ? <p className="notice error">{assignedTestError}</p> : null}

            <div className="account-tabs import-tabs admin-secondary-tabs" role="tablist" aria-label="Assigned test administration">
              <button type="button" role="tab" aria-selected={testTab === "create"} className={testTab === "create" ? "tab active" : "tab"} onClick={() => setTestTab("create")}>Create Tests</button>
              <button type="button" role="tab" aria-selected={testTab === "history"} className={testTab === "history" ? "tab active" : "tab"} onClick={() => setTestTab("history")}>Manage Tests</button>
              <button type="button" role="tab" aria-selected={testTab === "assign"} className={testTab === "assign" ? "tab active" : "tab"} onClick={() => setTestTab("assign")}>Assign Test(s)</button>
              <button type="button" role="tab" aria-selected={testTab === "results"} className={testTab === "results" ? "tab active" : "tab"} onClick={() => setTestTab("results")}>Results</button>
            </div>

            {testTab === "history" ? (
            <div className="card table-card">
              <div className="section-header compact-section-header">
                <h3>Historical tests</h3>
                <p>{assignedTests.length} test(s)</p>
              </div>
              <div className="dashboard-actions">
                <button type="button" className="secondary-button compact-button" onClick={() => loadAssignedTests().catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to load tests."))}>Refresh</button>
              </div>
              <div className="table-wrap">
                <table className="data-table">
                  <thead><tr><th>Public key</th><th>Name</th><th>Status</th><th>Availability</th><th>Questions</th><th>Assigned</th><th>Submitted</th><th>Results</th><th>Created</th><th>Actions</th></tr></thead>
                  <tbody>
                    {assignedTests.map((test) => {
                      const displayStatus = displayAdminTestStatus(test);
                      const statusLabel = adminTestStatusMeaningByCode.get(displayStatus) ?? displayStatus;
                      const canActivate = test.status === "DRAFT";
                      const canDelete = test.status === "DRAFT" || test.status === "ACTIVE";
                      return (
                        <tr key={test.versionId}>
                          <td>{test.publicKey}</td>
                          <td>{test.name}</td>
                          <td>{statusLabel}</td>
                          <td>{availabilityLabel(test)}<br /><small>{formatDateTime(test.availableFrom)} - {formatDateTime(test.availableUntil)}</small></td>
                          <td>{test.questionCount}</td>
                          <td>{test.assignedCount}</td>
                          <td>{test.submittedCount}</td>
                          <td>{test.resultsPublishedAt ? formatDateTime(test.resultsPublishedAt) : "Not published"}</td>
                          <td>{formatDateTime(test.createdAt)}</td>
                          <td>
                            <div className="table-actions">
                              {canActivate ? (
                                <button type="button" className="secondary-button compact-button" onClick={() => activateAssignedTest(test).catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to activate test."))}>Activate</button>
                              ) : null}
                              {canDelete ? (
                                <button type="button" className="secondary-button compact-button" onClick={() => deleteAssignedTest(test).catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to delete test."))}>Delete</button>
                              ) : <span className="muted">Locked</span>}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                    {assignedTests.length === 0 ? (
                      <tr><td colSpan={10}>No tests created yet.</td></tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </div>
            ) : null}

            {testTab === "create" ? (
              <>
            <form className="card account-form" onSubmit={createAssignedTest}>
              <div className="section-header compact-section-header">
                <h3>Create draft test</h3>
                <p>{selectedAssignedQuestionIds.length} selected question(s). Activate the draft before assigning it.</p>
              </div>
              <div className="form-grid">
                <label>
                  Test public key
                  <input
                    value={assignedTestForm.publicKey}
                    onChange={(event) => setAssignedTestForm((current) => ({ ...current, publicKey: event.target.value.toUpperCase() }))}
                    placeholder="GRADE5_MATH_TEST_001"
                    required
                  />
                </label>
                <label>
                  Test name
                  <input
                    value={assignedTestForm.name}
                    onChange={(event) => setAssignedTestForm((current) => ({ ...current, name: event.target.value }))}
                    placeholder="Grade 5 Maths Baseline"
                    required
                  />
                </label>
                <label>
                  Time allowed (minutes)
                  <input
                    type="number"
                    min="1"
                    value={assignedTestForm.timeAllowedMinutes}
                    onChange={(event) => setAssignedTestForm((current) => ({ ...current, timeAllowedMinutes: Number(event.target.value) }))}
                  />
                </label>
                <label>
                  Available from
                  <input
                    type="datetime-local"
                    value={assignedTestForm.availableFrom}
                    onChange={(event) => setAssignedTestForm((current) => ({ ...current, availableFrom: event.target.value }))}
                  />
                </label>
                <label>
                  Available until
                  <input
                    type="datetime-local"
                    value={assignedTestForm.availableUntil}
                    onChange={(event) => setAssignedTestForm((current) => ({ ...current, availableUntil: event.target.value }))}
                  />
                </label>
              </div>
              <div className="dashboard-actions">
                <button type="submit" className="primary-button compact-button" disabled={creatingAssignedTest}>
                  {creatingAssignedTest ? "Creating..." : "Create draft"}
                </button>
                <button type="button" className="secondary-button compact-button" onClick={() => setSelectedAssignedQuestionIds([])}>Clear selected</button>
              </div>
              {selectedAssignedQuestions.length > 0 ? (
                <div className="selected-question-list">
                  {selectedAssignedQuestions.map((question, index) => (
                    <span key={question.id}>{index + 1}. {question.questionText}</span>
                  ))}
                </div>
              ) : null}
            </form>

            <div className="dashboard-actions">
              <label className="inline-select">
                Search
                <input value={questionSearch} onChange={(event) => {
                  resetTestQuestionCursor();
                  setQuestionSearch(event.target.value);
                }} placeholder="Search questions" />
              </label>
              <label className="inline-select">
                Node filter
                <select value={questionNodeFilterId} onChange={(event) => {
                  resetTestQuestionCursor();
                  setQuestionNodeFilterId(event.target.value);
                }}>
                  <option value="">All active taxonomy</option>
                  {[...allNodes]
                    .filter((node) => isActiveTaxonomyBranch(node.id))
                    .sort((left, right) => left.displayName.localeCompare(right.displayName))
                    .map((node) => (
                    <option key={node.id} value={node.id}>{node.displayName}</option>
                  ))}
                </select>
              </label>
              <label className="inline-select">
                Difficulty
                <select value={questionDifficultyFilter} onChange={(event) => {
                  resetTestQuestionCursor();
                  setQuestionDifficultyFilter(event.target.value);
                }}>
                  {difficultyLookups.map((lookup) => <option key={lookup.id} value={lookup.lookupCode === "ALL" ? "" : lookup.lookupCode}>{lookup.lookupMeaning}</option>)}
                </select>
              </label>
              <label className="inline-select">
                Workflow
                <span className="muted">Active, Approved, Practice</span>
              </label>
            </div>

            <div className="card table-card">
              <h3>Pick questions</h3>
              {testQuestionLoading ? <p className="muted">Searching questions...</p> : null}
              <div className="question-list">
                {testQuestionResults.map((question) => (
                  <label className="question-row-card selectable-question-row" key={question.id}>
                    <input
                      type="checkbox"
                      checked={selectedAssignedQuestionIds.includes(question.id)}
                      onChange={() => toggleAssignedQuestion(question.id)}
                    />
                    <span>
                      <span className="question-summary-text">{question.questionText}</span>
                      <small>{question.taxonomyNodeLabel}</small>
                    </span>
                    <small>{question.difficulty} | {question.questionType}</small>
                  </label>
                ))}
                {!testQuestionLoading && testQuestionResults.length === 0 ? (
                  <p className="notice warning">No matching questions found.</p>
                ) : null}
              </div>
              <div className="pagination-bar">
                <span>Page {testQuestionCursorIndex + 1}</span>
                <div className="pagination-actions">
                  <button type="button" className="secondary-button compact-button" disabled={testQuestionCursorIndex <= 0 || testQuestionLoading} onClick={() => loadTestQuestionPage(testQuestionCursorIndex - 1)}>Previous</button>
                  <button type="button" className="secondary-button compact-button" disabled={!testQuestionHasNext || !testQuestionNextCursor || testQuestionLoading} onClick={() => loadTestQuestionPage(testQuestionCursorIndex + 1)}>Next</button>
                </div>
              </div>
            </div>
              </>
            ) : null}

            {testTab === "assign" ? (
            <div className="card import-step-card">
              <div className="section-header compact-section-header">
                <h3>Assign tests</h3>
                <p>Assign one student manually, or use CSV for bulk assignments.</p>
              </div>
              <div className="dashboard-actions import-actions">
                <label className="inline-select">
                  Test
                  <select value={selectedAssignedTestVersionId} onChange={(event) => setSelectedAssignedTestVersionId(event.target.value)}>
                    <option value="">Select active or published test</option>
                    {assignableAssignedTests.map((test) => (
                      <option key={test.versionId} value={test.versionId}>{test.publicKey} - {test.name}</option>
                    ))}
                  </select>
                </label>
                <label className="inline-select">
                  Student email, username, or subject
                  <input
                    value={manualAssignmentStudentSubject}
                    onChange={(event) => setManualAssignmentStudentSubject(event.target.value)}
                    placeholder="r@g"
                  />
                </label>
                <button
                  type="button"
                  className="primary-button compact-button"
                  onClick={() => assignTestToStudent().catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to assign test."))}
                >
                  Assign student
                </button>
              </div>
              <div className="section-header compact-section-header">
                <h3>Bulk assignment CSV</h3>
                <p>CSV columns: TestPublicKey, StudentSubject. Draft tests must be activated before assignment.</p>
              </div>
              <div className="dashboard-actions import-actions">
                <button type="button" className="secondary-button compact-button" onClick={() => {
                  const blob = new Blob(["TestPublicKey,StudentSubject\nGRADE5_MATH_TEST_001,keycloak-user-subject\n"], { type: "text/csv;charset=utf-8" });
                  const url = URL.createObjectURL(blob);
                  const anchor = document.createElement("a");
                  anchor.href = url;
                  anchor.download = "assigned-test-assignments-template.csv";
                  anchor.click();
                  URL.revokeObjectURL(url);
                }}>Template</button>
                <label className="file-drop compact-file-drop">
                  Choose CSV
                  <input type="file" accept=".csv,text/csv" onChange={(event) => setAssignedTestImportFile(event.target.files?.[0] ?? null)} />
                </label>
                {assignedTestImportFile ? <span className="muted selected-file-name">{assignedTestImportFile.name}</span> : null}
                {assignedTestImportFile ? (
                  <button type="button" className="primary-button compact-button" onClick={() => importAssignedTestAssignments().catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to start assignment import."))}>
                    Upload and import
                  </button>
                ) : null}
                {assignedTestImportJob ? (
                  <button type="button" className="secondary-button compact-button" onClick={() => refreshAssignedTestImportJob().catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to refresh import job."))}>
                    Refresh job
                  </button>
                ) : null}
              </div>
              {assignedTestImportJob ? (
                <p className="notice success">
                  Job {assignedTestImportJob.status}: {assignedTestImportJob.importedRows} imported, {assignedTestImportJob.skippedRows} skipped, {assignedTestImportJob.failedRows} failed.
                </p>
              ) : null}
              {assignedTestRows.length > 0 ? (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead><tr><th>Line</th><th>Test</th><th>Student</th><th>Status</th><th>Message</th></tr></thead>
                    <tbody>
                      {assignedTestRows.slice(0, 50).map((row) => (
                        <tr key={`${row.lineNumber}-${row.studentSubject}`}>
                          <td>{row.lineNumber}</td>
                          <td>{row.testPublicKey}</td>
                          <td>{row.studentSubject}</td>
                          <td>{row.status}</td>
                          <td>{row.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </div>
            ) : null}

            {testTab === "results" ? (
            <div className="card table-card">
              <div className="section-header compact-section-header">
                <h3>Results</h3>
                <p>{submittedAssignedTestResults.length} submitted of {assignedTestResults.length} assigned student(s)</p>
              </div>
              <div className="dashboard-actions">
                <label className="inline-select">
                  Test
                  <select value={selectedAssignedTestVersionId} onChange={(event) => setSelectedAssignedTestVersionId(event.target.value)}>
                    <option value="">Select test</option>
                    {resultEligibleAssignedTests.map((test) => (
                      <option key={test.versionId} value={test.versionId}>{test.publicKey} - {test.name}</option>
                    ))}
                  </select>
                </label>
                <button type="button" className="secondary-button compact-button" onClick={() => loadAssignedTests().catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to load tests."))}>Refresh</button>
                <button type="button" className="primary-button compact-button" disabled={!selectedAssignedTestVersionId || submittedAssignedTestResults.length === 0} onClick={() => publishAssignedTestResults().catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to publish results."))}>Publish all submitted</button>
              </div>
              <div className="table-wrap">
                <table className="data-table">
                  <thead><tr><th>Student</th><th>Status</th><th>Assigned</th><th>Submitted</th><th>Score</th><th>Published</th><th>Actions</th></tr></thead>
                  <tbody>
                    {assignedTestResults.map((result) => (
                      <Fragment key={result.assignmentId}>
                        <tr>
                          <td>{result.studentSubject}</td>
                          <td>{result.status}</td>
                          <td>{formatDateTime(result.assignedAt)}</td>
                          <td>{formatDateTime(result.submittedAt)}</td>
                          <td>{result.scorePoints ?? 0} / {result.maxPoints}</td>
                          <td>{result.resultsPublishedAt ? formatDateTime(result.resultsPublishedAt) : "Not published"}</td>
                          <td>
                            <div className="dashboard-actions compact-result-actions">
                              <button type="button" className="secondary-button compact-button" disabled={result.status !== "SUBMITTED"} onClick={() => loadAssignedTestResultDetail(result).catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to load result detail."))}>
                                {expandedAssignedResultId === result.assignmentId ? "Hide" : "Edit result"}
                              </button>
                              <button type="button" className="primary-button compact-button" disabled={result.status !== "SUBMITTED" || Boolean(result.resultsPublishedAt)} onClick={() => publishAssignedTestStudentResult(result).catch((exception) => setAssignedTestError(exception instanceof Error ? exception.message : "Unable to publish student result."))}>
                                Publish
                              </button>
                            </div>
                          </td>
                        </tr>
                        {expandedAssignedResultId === result.assignmentId && assignedTestResultDetails[result.assignmentId]?.attempt?.questions?.length ? (
                          <tr key={`${result.assignmentId}-review`}>
                            <td colSpan={7}>
                              <div className="admin-result-review">
                                {assignedTestResultDetails[result.assignmentId].attempt!.questions.map((question) => (
                                  <div key={question.attemptQuestionId} className="admin-result-question">
                                    <strong>{question.questionNumber}. {question.questionText}</strong>
                                    <span>Your answer: {resultSubmittedAnswerText(question)}</span>
                                    <span>Correct answer: {resultCorrectAnswerText(question)}</span>
                                    <span>{question.correct ? "Correct" : "Incorrect"}</span>
                                  </div>
                                ))}
                              </div>
                            </td>
                          </tr>
                        ) : null}
                      </Fragment>
                    ))}
                    {assignedTestResults.length === 0 ? (
                      <tr><td colSpan={7}>No assignments or results for this test yet.</td></tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </div>
            ) : null}
          </section>
        ) : null}

        {activeTab === "import" ? (
          <section className="section">
            <div className="section-header">
              <h2>Import</h2>
            </div>
            <div className="account-tabs import-tabs" role="tablist" aria-label="Import type">
              <button type="button" role="tab" aria-selected={importTab === "csv"} className={importTab === "csv" ? "tab active" : "tab"} onClick={() => setImportTab("csv")}>CSV import</button>
              <button type="button" role="tab" aria-selected={importTab === "json"} className={importTab === "json" ? "tab active" : "tab"} onClick={() => setImportTab("json")}>JSON import</button>
            </div>
            {importTab === "csv" ? (
              <>
                <div className="import-step-tabs" role="tablist" aria-label="CSV import steps">
                  {importMetadata.map((step) => (
                    <button
                      key={step.stepCode}
                      type="button"
                      role="tab"
                      aria-selected={activeImportStep === step.stepCode}
                      className={activeImportStep === step.stepCode ? "tab active" : "tab"}
                      onClick={() => setActiveImportStep(step.stepCode)}
                    >
                      Step {step.sequence}: {step.label.replace("Import ", "")}
                    </button>
                  ))}
                </div>
                {importMetadata.map((step) => {
                  if (step.stepCode !== activeImportStep) return null;
                  const preview = importPreviews[step.stepCode];
                  const summary = importSummaries[step.stepCode];
                  const selectedFile = selectedImportFiles[step.stepCode];
                  const previewErrors = preview?.rows.flatMap((row) => row.errors) ?? [];
                  const previewWarnings = preview?.rows.flatMap((row) => row.warnings ?? []) ?? [];
                  const summaryErrors = summary?.rows.flatMap((row) => row.errors) ?? [];
                  const summaryWarnings = summary?.rows.flatMap((row) => row.warnings ?? []) ?? [];
                  const visibleErrors = [...previewErrors, ...summaryErrors];
                  const visibleWarnings = [...previewWarnings, ...summaryWarnings];
                  return (
                    <div className="card import-step-card" key={step.stepCode}>
                      <div className="section-header compact-section-header">
                        <h3>Step {step.sequence}: {step.label}</h3>
                      </div>
                      <div className="dashboard-actions import-actions">
                        <button type="button" className="secondary-button compact-button" onClick={() => downloadCsvTemplate(step)}>Template</button>
                        <label className="file-drop compact-file-drop">
                          Choose CSV
                          <input
                            type="file"
                            accept=".csv,text/csv"
                            onChange={(event) => {
                              const file = event.target.files?.[0] ?? null;
                              setCsvError("");
                              setSelectedImportFiles((current) => ({ ...current, [step.stepCode]: file }));
                            }}
                          />
                        </label>
                        {selectedFile ? <span className="muted selected-file-name">{selectedFile.name}</span> : null}
                        {selectedFile ? (
                          <button
                            type="button"
                            className="primary-button compact-button"
                            onClick={() => uploadImportCsv(step.stepCode, selectedFile).catch((exception) => setCsvError(exception instanceof Error ? exception.message : "Unable to upload CSV."))}
                          >
                            Upload
                          </button>
                        ) : null}
                        {preview ? (
                          <button
                            type="button"
                            className="primary-button compact-button"
                            disabled={preview.validRows === 0}
                            onClick={() => importCsvStep(step.stepCode).catch((exception) => setCsvError(exception instanceof Error ? exception.message : "Unable to import CSV."))}
                          >
                            Import valid rows
                          </button>
                        ) : null}
                      </div>
                      <div className="import-message-stack">
                        {csvError ? <p className="notice error">{csvError}</p> : null}
                        {importObjectKeys[step.stepCode] ? <p className="notice success">Stored object: {importObjectKeys[step.stepCode]}</p> : null}
                        {preview ? <p className="notice success">{preview.validRows} valid row(s), {preview.invalidRows} invalid row(s).</p> : null}
                        {summary ? <p className="notice success">Imported {summary.importedRows} row(s), failed {summary.failedRows} row(s).</p> : null}
                        {visibleErrors.length > 0 ? (
                          <p className="notice error">
                            {visibleErrors.slice(0, 5).join("; ")}
                            {visibleErrors.length > 5 ? `; ${visibleErrors.length - 5} more error(s)` : ""}
                          </p>
                        ) : null}
                        {visibleWarnings.length > 0 ? (
                          <p className="notice warning">
                            {visibleWarnings.slice(0, 5).join("; ")}
                            {visibleWarnings.length > 5 ? `; ${visibleWarnings.length - 5} more warning(s)` : ""}
                          </p>
                        ) : null}
                      </div>
                      <div className="table-wrap">
                        <table className="data-table">
                          <thead>
                            <tr>
                              <th>Column</th>
                              <th>Required</th>
                              <th>Description</th>
                            </tr>
                          </thead>
                          <tbody>
                            {step.columns.map((column) => (
                              <tr key={column.name}>
                                <td>{column.name}</td>
                                <td>{column.required ? "Yes" : "No"}</td>
                                <td>{column.description}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                      {preview ? (
                        <>
                          <div className="table-wrap">
                            <table className="data-table">
                              <thead>
                                <tr>
                                  <th>Line</th>
                                  <th>Valid</th>
                                  <th>Values</th>
                                  <th>Errors</th>
                                  <th>Warnings</th>
                                </tr>
                              </thead>
                              <tbody>
                                {preview.rows.map((row) => (
                                  <tr key={row.lineNumber}>
                                    <td>{row.lineNumber}</td>
                                    <td>{row.valid ? "Yes" : "No"}</td>
                                    <td>{Object.entries(row.values).map(([key, value]) => `${key}: ${value}`).join("; ")}</td>
                                    <td>{row.errors.join("; ")}</td>
                                    <td>{row.warnings?.join("; ") ?? ""}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </>
                      ) : null}
                    </div>
                  );
                })}
              </>
            ) : null}
            {importTab === "json" ? (
              <div className="card import-placeholder">
                <h3>JSON import</h3>
                <p className="muted">JSON import is not available yet.</p>
              </div>
            ) : null}
          </section>
        ) : null}
      </section>
    </main>
  );
}

export default function AdminPage() {
  return <AdminConsole />;
}
