import { Component, type ErrorInfo, type ReactNode } from 'react';

type Props = Readonly<{ children: ReactNode }>;
type State = Readonly<{ hasError: boolean; message: string | null }>;

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, message: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error.message };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('ErrorBoundary caught:', error, info.componentStack);
  }

  private handleReset = (): void => {
    this.setState({ hasError: false, message: null });
  };

  override render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div className="error-boundary">
          <h2>문제가 발생했습니다</h2>
          <p>{this.state.message ?? '알 수 없는 오류'}</p>
          <button className="primary-button" onClick={this.handleReset}>
            다시 시도
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
