import { useCallback, useEffect, useMemo, useState } from 'react';
import './style.css';

const API = '/api';

function money(minor, currency = 'INR') {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency', currency, minimumFractionDigits: 2
  }).format((minor || 0) / 100);
}

function toMinorUnits(raw) {
  const value = String(raw ?? '').trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  try {
    const minor = BigInt(whole) * 100n + BigInt((fraction + '00').slice(0, 2));
    if (minor <= 0n || minor > BigInt(Number.MAX_SAFE_INTEGER)) return null;
    return Number(minor);
  } catch {
    return null;
  }
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

function AuditTable({ events }) {
  return (
    <div className="panel table-panel">
      {events.length === 0 ? <p className="empty-state">No audit events recorded yet.</p> :
        <div className="table-scroll"><table><thead><tr>
          <th>Time</th><th>Actor</th><th>Action</th><th>Outcome</th><th>Resource / request</th><th>Reason</th>
        </tr></thead><tbody>{events.map((event) => <tr key={event.id}>
          <td>{new Date(event.occurredAt).toLocaleString()}</td>
          <td>{event.actorUsername || 'Unauthenticated'}</td>
          <td>{event.actionType}</td>
          <td><span className={event.outcome === 'SUCCESS' ? 'positive' : 'negative'}>{event.outcome}</span></td>
          <td className="mono">{event.resourceId || event.requestPath || '—'}</td>
          <td>{event.reason || event.details || '—'}</td>
        </tr>)}</tbody></table></div>}
    </div>
  );
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
  const [pendingKeys, setPendingKeys] = useState({});
  const [adminUsers, setAdminUsers] = useState([]);
  const [allAccounts, setAllAccounts] = useState([]);
  const [auditEvents, setAuditEvents] = useState([]);
  const [allTransactions, setAllTransactions] = useState([]);
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newRole, setNewRole] = useState('ACCOUNT_HOLDER');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const role = user?.authorities?.find((authority) => authority.startsWith('ROLE_'))
    ?.replace('ROLE_', '') || '';
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

  const loadAdminData = useCallback(async (auth) => {
    const [users, accountRows, events, transactions] = await Promise.all([
      request('/admin/users', auth),
      request('/admin/accounts', auth),
      request('/auditor/audit-events', auth),
      request('/auditor/transactions', auth)
    ]);
    setAdminUsers(users);
    setAllAccounts(accountRows);
    setAuditEvents(events);
    setAllTransactions(transactions);
  }, []);

  const loadAuditorData = useCallback(async (auth) => {
    const [accountRows, transactions, events] = await Promise.all([
      request('/auditor/accounts', auth),
      request('/auditor/transactions', auth),
      request('/auditor/audit-events', auth)
    ]);
    setAllAccounts(accountRows);
    setAllTransactions(transactions);
    setAuditEvents(events);
  }, []);

  const signIn = async (event) => {
    event.preventDefault();
    setError('');
    setBusy(true);
    const auth = loginName.trim() + ':' + loginPassword;
    try {
      const identity = await request('/access/me', auth);
      const assignedRole = identity.authorities?.find((authority) => authority.startsWith('ROLE_'))
        ?.replace('ROLE_', '');
      if (assignedRole === 'ADMIN') await loadAdminData(auth);
      else if (assignedRole === 'AUDITOR') await loadAuditorData(auth);
      else if (assignedRole === 'ACCOUNT_HOLDER') await loadAccounts(auth);
      else throw new Error('This account has no supported LedgerLine role.');
      setCredentials(auth);
      setUser(identity);
      setLoginPassword('');
    } catch (err) {
      setCredentials('');
      setUser(null);
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
    setAdminUsers([]);
    setAllAccounts([]);
    setAuditEvents([]);
    setAllTransactions([]);
    setPendingKeys({});
    setNotice('');
    setError('');
  };

  const refresh = async () => {
    if (!credentials) return;
    setBusy(true);
    try {
      if (role === 'ADMIN') await loadAdminData(credentials);
      else if (role === 'AUDITOR') await loadAuditorData(credentials);
      else await loadAccounts(credentials);
      setNotice('Information refreshed.');
      setError('');
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
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

  const createUser = async (event) => {
    event.preventDefault();
    setBusy(true); setError(''); setNotice('');
    try {
      await request('/admin/users', credentials, {
        method: 'POST',
        body: JSON.stringify({ username: newUsername.trim(), password: newPassword, role: newRole })
      });
      setNewUsername('');
      setNewPassword('');
      await loadAdminData(credentials);
      setNotice(`${newRole.replace('_', ' ').toLowerCase()} account created successfully.`);
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

  const transact = async (kind) => {
    const rawAmount = kind === 'deposit' ? depositAmount
      : kind === 'withdraw' ? withdrawAmount : transferAmount;
    const amountMinor = toMinorUnits(rawAmount);
    if (!activeAccount || amountMinor === null) {
      setError('Enter a valid positive amount with no more than two decimal places.');
      return;
    }
    if (kind === 'transfer' && !destination.trim()) {
      setError('Enter the destination account ID.');
      return;
    }
    const path = kind === 'transfer' ? '/account-holder/transactions/transfer'
      : `/account-holder/transactions/${kind}`;
    const payload = kind === 'transfer'
      ? { fromAccountId: activeAccount.id, toAccountId: destination.trim(), amountMinor }
      : { accountId: activeAccount.id, amountMinor };
    const signature = JSON.stringify([kind, activeAccount.id, destination.trim(), amountMinor]);
    const pending = pendingKeys[kind];
    const idempotencyKey = pending?.signature === signature ? pending.key : crypto.randomUUID();
    setPendingKeys((current) => ({ ...current, [kind]: { signature, key: idempotencyKey } }));
    setBusy(true); setError(''); setNotice('');
    try {
      await request(path, credentials, {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(payload)
      });
      if (kind === 'deposit') setDepositAmount('');
      if (kind === 'withdraw') setWithdrawAmount('');
      if (kind === 'transfer') setTransferAmount('');
      setPendingKeys((current) => { const next = { ...current }; delete next[kind]; return next; });
      await loadAccounts(credentials);
      await loadStatement(activeAccount.id);
      setNotice(kind === 'transfer' ? 'Transfer completed.' : `${kind[0].toUpperCase() + kind.slice(1)} completed.`);
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  useEffect(() => {
    if (activeAccount?.id && credentials && role === 'ACCOUNT_HOLDER') loadStatement(activeAccount.id);
  }, [activeAccount?.id, credentials, role]);

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
        <div className="user-menu"><span>{user.username}</span><span className="role-chip">{role}</span><button className="button-quiet" onClick={signOut}>Sign out</button></div>
      </header>
      <section className="welcome">
        <div><p className="eyebrow">{role} WORKSPACE</p><h1>Good to see you, {user.username}.</h1>
          <p className="muted">{role === 'ACCOUNT_HOLDER' ? 'Your accounts and recent ledger activity.' : role === 'ADMIN' ? 'Identity provisioning and system-wide oversight.' : 'Read-only financial activity and audit evidence.'}</p></div>
        <button className="button-secondary" onClick={refresh} disabled={busy}>{busy ? 'Refreshing…' : 'Refresh data'}</button>
      </section>
      {notice && <p className="alert success">{notice}</p>}
      {error && <p className="alert error">{error}</p>}

      {role === 'ACCOUNT_HOLDER' && <>
        <section className="overview-grid">
          <article className="panel balance-panel">
            <div className="panel-heading"><div><p className="eyebrow">SELECTED ACCOUNT</p><h2>{activeAccount?.accountNumber || 'No account yet'}</h2></div><span className="status-pill">{activeAccount?.status || 'NEW'}</span></div>
            <p className="balance-label">Available balance</p>
            <p className="balance">{activeAccount ? money(activeAccount.balanceMinor, activeAccount.currency) : '—'}</p>
            <div className="account-meta"><span>{activeAccount?.currency || 'Create an account to begin'}</span><span>{accounts.length} account{accounts.length === 1 ? '' : 's'}</span></div>
            {activeAccount && <p className="account-id">Account ID: <code>{activeAccount.id}</code></p>}
            {accounts.length > 0 && <label className="select-account">Switch account<select value={activeAccount?.id || ''} onChange={(e) => setSelected(e.target.value)}>{accounts.map((a) => <option key={a.id} value={a.id}>{a.accountNumber} · {a.currency}</option>)}</select></label>}
          </article>
          <article className="panel create-panel">
            <p className="eyebrow">GET STARTED</p><h2>Open an account</h2><p className="muted">Create an account with a supported three-letter currency code.</p>
            <div className="inline-form"><select value={currency} onChange={(e) => setCurrency(e.target.value)}><option value="INR">INR — Indian Rupee</option><option value="USD">USD — US Dollar</option><option value="EUR">EUR — Euro</option></select><button onClick={createAccount} disabled={busy}>Create account</button></div>
          </article>
        </section>
        <section className="section-block">
          <div className="section-title"><div><p className="eyebrow">MONEY MOVEMENT</p><h2>Make a transaction</h2></div><span className="muted small">Amounts are entered in major units</span></div>
          <div className="transaction-grid">
            <article className="panel transaction-card"><span className="transaction-icon deposit">＋</span><h3>Deposit</h3><p className="muted">Add funds to your selected account.</p><label>Amount<input type="text" inputMode="decimal" placeholder="0.00" value={depositAmount} onChange={(e) => setDepositAmount(e.target.value)} /></label><button onClick={() => transact('deposit')} disabled={busy || !activeAccount}>Deposit funds</button></article>
            <article className="panel transaction-card"><span className="transaction-icon withdraw">−</span><h3>Withdraw</h3><p className="muted">Withdraw from your selected account.</p><label>Amount<input type="text" inputMode="decimal" placeholder="0.00" value={withdrawAmount} onChange={(e) => setWithdrawAmount(e.target.value)} /></label><button className="button-secondary" onClick={() => transact('withdraw')} disabled={busy || !activeAccount}>Withdraw funds</button></article>
            <article className="panel transaction-card"><span className="transaction-icon transfer">↗</span><h3>Transfer</h3><p className="muted">Move funds to another account.</p><label>Destination account ID<input value={destination} onChange={(e) => setDestination(e.target.value)} placeholder="Paste destination account UUID" /></label><label>Amount<input type="text" inputMode="decimal" placeholder="0.00" value={transferAmount} onChange={(e) => setTransferAmount(e.target.value)} /></label><button className="button-secondary" onClick={() => transact('transfer')} disabled={busy || !activeAccount || !destination}>Transfer funds</button></article>
          </div>
        </section>
        <section className="section-block">
          <div className="section-title"><div><p className="eyebrow">APPEND-ONLY LEDGER</p><h2>Account statement</h2></div><button className="button-quiet" onClick={() => loadStatement(activeAccount?.id)}>Reload statement</button></div>
          <div className="panel table-panel">
            {statement.length === 0 ? <p className="empty-state">No transactions yet. Your posted activity will appear here.</p> :
              <div className="table-scroll"><table><thead><tr><th>Time</th><th>Type</th><th>Initiated by</th><th>Entry</th><th>Amount</th><th>Running balance</th></tr></thead><tbody>{statement.map((row) => <tr key={row.id}>
                <td>{new Date(row.createdAt).toLocaleString()}</td><td>{row.type}</td><td>{row.createdBy}</td><td className="mono">{row.id.slice(0, 8)}</td>
                <td className={row.amountMinor >= 0 ? 'positive' : 'negative'}>{row.amountMinor > 0 ? '+' : ''}{money(row.amountMinor, activeAccount?.currency)}</td>
                <td>{money(row.balanceAfterMinor, activeAccount?.currency)}</td>
              </tr>)}</tbody></table></div>}
          </div>
        </section>
      </>}

      {role === 'ADMIN' && <>
        <section className="section-block">
          <div className="section-title"><div><p className="eyebrow">ACCESS MANAGEMENT</p><h2>Create a user</h2></div></div>
          <form className="panel admin-form" onSubmit={createUser}>
            <label>Username<input minLength="3" maxLength="80" pattern="[A-Za-z0-9._-]+" value={newUsername} onChange={(e) => setNewUsername(e.target.value)} required /></label>
            <label>Temporary password<input type="password" minLength="12" maxLength="72" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required /></label>
            <label>Role<select value={newRole} onChange={(e) => setNewRole(e.target.value)}><option value="ACCOUNT_HOLDER">Account holder</option><option value="AUDITOR">Auditor</option></select></label>
            <button disabled={busy}>Create account</button>
          </form>
        </section>
        <section className="section-block">
          <div className="section-title"><div><p className="eyebrow">IDENTITIES</p><h2>Registered users ({adminUsers.length})</h2></div></div>
          <div className="panel table-panel"><div className="table-scroll"><table><thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Created</th></tr></thead><tbody>{adminUsers.map((u) => <tr key={u.username}><td>{u.username}</td><td>{u.role}</td><td>{u.enabled ? 'Enabled' : 'Disabled'}</td><td>{new Date(u.createdAt).toLocaleString()}</td></tr>)}</tbody></table></div></div>
        </section>
      </>}

      {(role === 'ADMIN' || role === 'AUDITOR') && <>
        <section className="section-block">
          <div className="section-title"><div><p className="eyebrow">ACCOUNT REGISTER</p><h2>All accounts ({allAccounts.length})</h2></div></div>
          <div className="panel table-panel"><div className="table-scroll"><table><thead><tr><th>Account</th><th>Owner</th><th>Currency</th><th>Balance</th><th>Status</th><th>Created</th></tr></thead><tbody>{allAccounts.map((a) => <tr key={a.id}><td className="mono">{a.accountNumber}</td><td>{a.ownerUsername}</td><td>{a.currency}</td><td>{money(a.balanceMinor, a.currency)}</td><td>{a.status}</td><td>{new Date(a.createdAt).toLocaleString()}</td></tr>)}</tbody></table></div></div>
        </section>
        <section className="section-block">
          <div className="section-title"><div><p className="eyebrow">DOUBLE-ENTRY LEDGER</p><h2>Recent transactions ({allTransactions.length})</h2></div></div>
          <div className="panel table-panel">{allTransactions.length === 0 ? <p className="empty-state">No transactions recorded.</p> :
            <div className="table-scroll"><table><thead><tr><th>Transaction</th><th>Type</th><th>Initiated by</th><th>Postings</th><th>Created</th></tr></thead><tbody>{allTransactions.map((tx) => <tr key={tx.id}>
              <td className="mono">{tx.id.slice(0, 8)}</td><td>{tx.type}</td><td>{tx.createdBy}</td>
              <td>{tx.postings.map((p, i) => <div key={i} className={p.amountMinor >= 0 ? 'positive' : 'negative'}>{p.accountType}: {p.accountReference.slice(0, 8)} · {p.amountMinor > 0 ? '+' : ''}{p.amountMinor}</div>)}</td>
              <td>{new Date(tx.createdAt).toLocaleString()}</td>
            </tr>)}</tbody></table></div>}
          </div>
        </section>
        <section className="section-block">
          <div className="section-title"><div><p className="eyebrow">TRACEABILITY</p><h2>Audit trail ({auditEvents.length})</h2></div></div>
          <AuditTable events={auditEvents} />
        </section>
      </>}

      <footer className="footer"><span>LedgerLine</span><span>Double-entry posting · PostgreSQL integrity controls · Auditable operations</span></footer>
    </main>
  );
}
