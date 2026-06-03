import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props { children: ReactNode }
interface State { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught:', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="p-6 text-center">
          <h2 className="text-xl font-bold mb-2">Đã có lỗi xảy ra</h2>
          <p className="text-gray-500 text-sm mb-4">{this.state.error.message}</p>
          <button
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-zalo text-white rounded-md"
          >
            Tải lại
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
