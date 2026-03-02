import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { ErrorBoundary } from './components/ErrorBoundary';
import './styles/tokens.css';
import './styles/reset.css';
import './styles/base.css';
import './styles/layout.css';
import './styles/cards.css';
import './styles/buttons.css';
import './styles/forms.css';
import './styles/badges.css';
import './styles/summary-card.css';
import './styles/source-card.css';
import './styles/detail-panel.css';
import './styles/dialog.css';
import './styles/toast.css';
import './styles/skeleton.css';
import './styles/profile.css';
import './styles/edit-form.css';
import './styles/filters.css';
import './styles/file-upload.css';
import './styles/states.css';
import './styles/overrides.css';
import './styles/animations.css';
import './styles/responsive.css';
import './styles/a11y.css';

const rootElement = document.getElementById('root');

if (rootElement === null) {
  throw new Error('root element not found');
}

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>
);
