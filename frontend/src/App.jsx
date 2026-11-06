import { useEffect, useState } from 'react';

export default function App() {
  const [apiStatus, setApiStatus] = useState('Checking');
  const [checkedAt, setCheckedAt] = useState('');

  useEffect(() => {
    let active = true;

    fetch('/api/health')
      .then((response) => {
        if (!response.ok) throw new Error('API unavailable');
        return response.json();
      })
      .then((data) => {
        if (!active) return;
        setApiStatus(data.status === 'UP' ? 'Connected' : 'Unavailable');
        setCheckedAt(new Date(data.timestamp).toLocaleTimeString());
      })
      .catch(() => {
        if (!active) return;
        setApiStatus('Not connected');
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="page-shell">
      <nav className="topbar">
        <a className="brand" href="/" aria-label="LedgerLine home">
          <span className="brand-mark">L</span>
          <span>LedgerLine</span>
        </a>
        <span className="environment">FOUNDATION BUILD</span>
      </nav>

      <section className="hero">
        <p className="eyebrow">FINANCIAL OPERATIONS, BUILT ON TRUST</p>
        <h1>Every transaction.<br /><span>Accounted for.</span></h1>
        <p className="intro">
          A foundation for secure, accurate, and traceable money movement.
          LedgerLine is being built with correctness at its core.
        </p>

        <div className="status-card" aria-live="polite">
          <div>
            <p className="card-label">BACKEND API STATUS</p>
            <p className="status-value">
              <span className={apiStatus === 'Connected' ? 'status-dot online' : 'status-dot'} />
              {apiStatus}
            </p>
            {checkedAt && <p className="checked-at">Last checked at {checkedAt}</p>}
          </div>
          <span className="status-tag">DAY 1</span>
        </div>
      </section>

      <section className="principles">
        <article>
          <span>01</span>
          <h2>Correctness</h2>
          <p>Balances must remain accurate and money must never appear or disappear.</p>
        </article>
        <article>
          <span>02</span>
          <h2>Authorization</h2>
          <p>Every operation must be permitted and tied to the right account owner.</p>
        </article>
        <article>
          <span>03</span>
          <h2>Traceability</h2>
          <p>Financial activity should be recorded and auditable from end to end.</p>
        </article>
      </section>

      <footer>
        <span>LedgerLine</span>
        <span>Foundation stage · Core transaction features are not implemented yet</span>
      </footer>
    </main>
  );
}
