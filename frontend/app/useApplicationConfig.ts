"use client";

import { useEffect, useState } from "react";
import { apiBaseUrl, defaultApplicationConfig, type ApplicationConfig } from "./applicationConfig";

export function useApplicationConfig() {
  const [config, setConfig] = useState<ApplicationConfig>(defaultApplicationConfig);

  useEffect(() => {
    const controller = new AbortController();
    fetch(`${apiBaseUrl}/api/public/application`, { signal: controller.signal })
      .then((response) => response.ok ? response.json() : null)
      .then((body) => {
        if (body?.applicationName) {
          setConfig({
            applicationCode: body.applicationCode ?? defaultApplicationConfig.applicationCode,
            applicationName: body.applicationName,
          });
        }
      })
      .catch((exception) => {
        if (exception instanceof DOMException && exception.name === "AbortError") return;
      });
    return () => controller.abort();
  }, []);

  return config;
}
