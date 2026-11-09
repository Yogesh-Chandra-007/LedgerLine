import { useCallback, useEffect, useMemo, useState } from 'react';
import './style.css';

const API = '/api';

function money(minor, currency = 'INR') {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency', currency, minimumFractionDigits: 2
  }).format((minor || 0) / 100);
}

async function request(path, credentials, options = {}) {
  const response = await fetch(API + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Basic ' + btoa(credentials),
      ...(options.headers || {})
    }
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const message = body?.detail || body?.message || body?.error || `Request failed (${response.status})`;
    throw new Error(message);
  }
  return body;
}

export default function App() {
  const [credentials, setCredentials] = useState('');
  const [loginName, setLoginName] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [user, setUser] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [selected, setSelected] = useState('');
  const [statement, setStatement] = useState([]);
  const [currency, setCurrency] = useState('INR');
  const [depositAmount, setDepositAmount] = useState('');
  const [withdrawAmount, setWithdrawAmount] = useState('');
  const [transferAmount, setTransferAmount] = useState('');
  const [destination, setDestination] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const activeAccount = useMemo(
    () => accounts.find((account) => account.id === selected) || accounts[0],
    [accounts, selected]
  );

  const loadAccounts = useCallback(async (auth) => {
    const result = await request('/account-holder/accounts', auth);
    setAccounts(result);
    setSelected((current) => current && result.some((a) => a.id === current)
      ? current : (result[0]?.id || ''));
  }, []);

  const signIn = async (event) => {
    event.preventDefault();
    setError('');
    setBusy(true);
    const auth = loginName.trim() + ':' + loginPassword;
    try {
      const identity = await request('/access/me', auth);
      setCredentials(auth);
      setUser(identity);
      setLoginPassword('');
      await loadAccounts(auth);
    } catch (err) {
      setError(err.message || 'Unable to sign in');
    } finally {
      setBusy(false);
    }
  };

  const signOut = () => {
    setCredentials('');
    setUser(null);
    setAccounts([]);
    setStatement([]);
    setSelected('');
    setNotice('');
    setError('');
  };

  const refresh = async () => {
    if (!credentials) return;
    try {
      await loadAccounts(credentials);
      setNotice('Account information refreshed.');
    } catch (err) { setError(err.message); }
  };

  const createAccount = async () => {
    setBusy(true); setError(''); setNotice('');
    try {
      const created = await request('/account-holder/accounts', credentials, {
        method: 'POST', body: JSON.stringify({ currency })
      });
      await loadAccounts(credentials);
      setSelected(created.id);
      setNotice('Your new account is ready.');
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  const transact = async (kind) => {
    const rawAmount = kind === 'deposit' ? depositAmount : kind === 'withdraw' ? withdrawAmount : transferAmount;
    if (!activeAccount || !rawAmount || Number(rawAmount) <= 0) {
      setError('Choose an account and enter a positive amount.');
      return;
    }
    if (!/^\\d+(\\.\\d{1,2})?$/.test(rawAmount)) {
      setError('Enter a valid amount with up to two decimal places.');
      return;
    }
    const amountMinor = Math.round(Number(rawAmount) * 100);
    if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) {
      setError('Enter a valid amount with up to two decimal places.');
      return;
    }
    const path = kind === 'transfer' ? '/account-holder/transactions/transfer'
      : `/account-holder/transactions/${kind}`;
    const payload = kind === 'transfer'
      ? { fromAccountId: activeAccount.id, toAccountId: destination, amountMinor }
      : { accountId: activeAccount.id, amountMinor };
    setBusy(true); setError(''); setNotice('');
    try {
      await request(path, credentials, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify(payload)
      });
      if (kind === 'deposit') setDepositAmount('');
      if (kind === 'withdraw') setWithdrawAmount('');
      if (kind === 'transfer') setTransferAmount('');
      await loadAccounts(credentials);
      await loadStatement(activeAccount.id);
      setNotice(kind === 'transfer' ? 'Transfer completed.' : `${kind[0].toUpperCase() + kind.slice(1)} completed.`);
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  const loadStatement = async (accountId) => {
    if (!accountId || !credentials) return;
    try {
      const rows = await request(`/account-holder/accounts/${accountId}/statement`, credentials);
      setStatement(rows);
    } catch (err) { setError(err.message); }
  };

  useEffect(() => {
    if (activeAccount?.id && credentials) loadStatement(activeAccount.id);
  }, [activeAccount?.id, credentials]);

  if (!user) {
    return (
      <main className="auth-shell">
        <section className="auth-card">
          <div className="brand"><span className="brand-mark">L</span><span>LedgerLine</span></div>
          <p className="eyebrow">SECURE FINANCIAL OPERATIONS</p>
          <h1>Your money,<br /><span>accounted for.</span></h1>
          <p className="muted">Sign in with the account credentials provided by your administrator.</p>
          <form onSubmit={signIn} className="login-form">
            <label>Username<input autoComplete="username" value={loginName} onChange={(e) => setLoginName(e.target.value)} required /></label>
            <label>Password<input type="password" autoComplete="current-password" value={loginPassword} onChange={(e) => setLoginPassword(e.target.value)} required /></label>
            {error && <p className="alert error">{error}</p>}
            <button disabled={busy}>{busy ? 'Signing in…' : 'Sign in securely'}</button>
          </form>
          <p className="fine-print">Credentials remain in memory for this browser tab and are cleared when you sign out or close the tab.</p>
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard-shell">
      <header className="topbar">
        <a className="brand" href="/"><span className="brand-mark">L</span><span>LedgerLine</span></a>
        <div className="user-menu"><span>{user.username}</span><span className="role-chip">{user.authorities?.[0]?.replace('ROLE_', '')}</span><button className="button-quiet" onClick={signOut}>Sign out</button></div>
      </header>
      <section className="welcome">
        <div><p className="eyebrow">PERSONAL FINANCE OVERVIEW</p><h1>Good to see you, {user.username}.</h1><p className="muted">Your accounts and recent ledger activity in one place.</p></div>
        <button className="button-secondary" onClick={refresh} disabled={busy}>Refresh data</button>
      </section>
      {notice && <p className="alert success">{notice}</p>}
      {error && <p className="alert error">{error}</p>}
      <section className="overview-grid">
        <article className="panel balance-panel">
          <div className="panel-heading"><div><p className="eyebrow">SELECTED ACCOUNT</p><h2>{activeAccount?.accountNumber || 'No account yet'}</h2></div><span className="status-pill">{activeAccount?.status || 'NEW'}</span></div>
          <p className="balance-label">Available balance</p>
          <p className="balance">{activeAccount ? money(activeAccount.balanceMinor, activeAccount.currency) : '—'}</p>
          <div className="account-meta"><span>{activeAccount?.currency || 'Create an account to begin'}</span><span>{accounts.length} account{accounts.length === 1 ? '' : 's'}</span></div>
          {accounts.length > 0 && <label className="select-account">Switch account<select value={activeAccount?.id || ''} onChange={(e) => setSelected(e.target.value)}>{accounts.map((a) => <option key={a.id} value={a.id}>{a.accountNumber} · {a.currency}</option>)}</select></label>}
        </article>
        <article className="panel create-panel">
          <p className="eyebrow">GET STARTED</p><h2>Open an account</h2><p className="muted">Create a new account with a supported three-letter currency code.</p>
          <div className="inline-form"><select value={currency} onChange={(e) => setCurrency(e.target.value)}><option value="INR">INR — Indian Rupee</option><option value="USD">USD — US Dollar</option><option value="EUR">EUR — Euro</option></select><button onClick={createAccount} disabled={busy}>Create account</button></div>
        </article>
      </section>
      <section className="section-block">
        <div className="section-title"><div><p className="eyebrow">MONEY MOVEMENT</p><h2>Make a transaction</h2></div><span className="muted small">Amounts are entered in major units</span></div>
        <div className="transaction-grid">
          <article className="panel transaction-card"><span className="transaction-icon deposit">＋</span><h3>Deposit</h3><p className="muted">Add funds to your selected account.</p><label>Amount<input type="number" min="0.01" step="0.01" placeholder="0.00" value={depositAmount} onChange={(e) => setDepositAmount(e.target.value)} /></label><button onClick={() => transact('deposit')} disabled={busy || !activeAccount}>Deposit funds</button></article>
          <article className="panel transaction-card"><span className="transaction-icon withdraw">−</span><h3>Withdraw</h3><p className="muted">Withdraw from your selected account.</p><label>Amount<input type="number" min="0.01" step="0.01" placeholder="0.00" value={withdrawAmount} onChange={(e) => setWithdrawAmount(e.target.value)} /></label><button className="button-secondary" onClick={() => transact('withdraw')} disabled={busy || !activeAccount}>Withdraw funds</button></article>
          <article className="panel transaction-card"><span className="transaction-icon transfer">↗</span><h3>Transfer</h3><p className="muted">Move funds to another account.</p><label>Destination account ID<input value={destination} onChange={(e) => setDestination(e.target.value)} placeholder="Paste destination account UUID" /></label><label>Amount<input type="number" min="0.01" step="0.01" placeholder="0.00" value={transferAmount} onChange={(e) => setTransferAmount(e.target.value)} /></label><button className="button-secondary" onClick={() => transact('transfer')} disabled={busy || !activeAccount || !destination}>Transfer funds</button></article>
        </div>
      </section>
      <section className="section-block">
        <div className="section-title"><div><p className="eyebrow">APPEND-ONLY LEDGER</p><h2>Recent activity</h2></div><button className="button-quiet" onClick={() => loadStatement(activeAccount?.id)}>Reload statement</button></div>
        <div className="panel table-panel">
          {statement.length === 0 ? <p className="empty-state">No transactions yet. Your posted activity will appear here.</p> : <div className="table-scroll"><table><thead><tr><th>Entry</th><th>Transaction</th><th>Amount</th><th>Posted at</th></tr></thead><tbody>{statement.map((row) => <tr key={row.id}><td className="mono">{row.id.slice(0, 8)}</td><td className="mono">{row.transactionId.slice(0, 8)}</td><td className={row.amountMinor >= 0 ? 'positive' : 'negative'}>{row.amountMinor > 0 ? '+' : ''}{money(row.amountMinor, activeAccount?.currency)}</td><td>{new Date(row.createdAt).toLocaleString()}</td></tr>)}</tbody></table></div>}
        </div>
      </section>
      <footer className="footer"><span>LedgerLine</span><span>Double-entry posting · Idempotent requests · Ownership checks</span></footer>
    </main>
  );
}
