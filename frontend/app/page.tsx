import { AccountAccessPanel } from "./account/AccountAccessPanel";

export default function Home() {
  return (
    <main className="account-shell">
      <AccountAccessPanel showBackLink={false} />
    </main>
  );
}
