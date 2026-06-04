export type ApplicationConfig = {
  applicationCode: string;
  applicationName: string;
};

export const defaultApplicationConfig: ApplicationConfig = {
  applicationCode: process.env.NEXT_PUBLIC_APPLICATION_CODE ?? "CLEVERLEAF",
  applicationName: process.env.NEXT_PUBLIC_APPLICATION_NAME ?? "CleverLeaf",
};

export const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";
