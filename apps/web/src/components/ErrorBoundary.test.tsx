import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

function ThrowingChild({ shouldThrow }: Readonly<{ shouldThrow: boolean }>) {
  if (shouldThrow) throw new Error('테스트 에러');
  return <div>정상 컴포넌트</div>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('renders children when no error', () => {
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={false} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('정상 컴포넌트')).toBeInTheDocument();
  });

  it('shows error UI with heading when child throws', () => {
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('문제가 발생했습니다')).toBeInTheDocument();
  });

  it('shows the error message from the caught error', () => {
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('테스트 에러')).toBeInTheDocument();
  });

  it('shows error message as empty string when Error has no message', () => {
    // Error('') results in message='', which is not null so fallback doesn't trigger
    function ThrowEmptyMsg(): never { throw new Error(''); }
    render(
      <ErrorBoundary>
        <ThrowEmptyMsg />
      </ErrorBoundary>,
    );
    // The paragraph shows '' — fallback '알 수 없는 오류' only triggers on null
    expect(screen.getByText('문제가 발생했습니다')).toBeInTheDocument();
  });

  it('recovers after reset button click', () => {
    let shouldThrow = true;
    function Toggler() {
      if (shouldThrow) throw new Error('에러');
      return <div>복구됨</div>;
    }

    render(
      <ErrorBoundary>
        <Toggler />
      </ErrorBoundary>,
    );
    expect(screen.getByText('문제가 발생했습니다')).toBeInTheDocument();

    shouldThrow = false;
    fireEvent.click(screen.getByText('다시 시도'));
    expect(screen.getByText('복구됨')).toBeInTheDocument();
  });

  it('logs error to console.error', () => {
    const spy = vi.mocked(console.error);
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    expect(spy).toHaveBeenCalled();
    const callArgs = spy.mock.calls.find(
      (args) => typeof args[0] === 'string' && args[0].includes('ErrorBoundary'),
    );
    expect(callArgs).toBeDefined();
  });
});
