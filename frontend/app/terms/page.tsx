"use client";

import { useApplicationConfig } from "../useApplicationConfig";

export default function TermsPage() {
  const { applicationName } = useApplicationConfig();
  return <main><div className="eyebrow">{applicationName}</div><h1>Terms</h1><p className="lede">{applicationName} is currently a private educational pilot. Use approved accounts only. Do not submit copied commercial educational content.</p></main>;
}
