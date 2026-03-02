import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FileDropZone } from './FileDropZone';

function getDropZone(container: HTMLElement): HTMLElement {
  return container.querySelector('.file-drop-zone')!;
}

describe('FileDropZone', () => {
  it('renders children', () => {
    render(
      <FileDropZone onFileSelect={vi.fn()}>
        <span>자식 요소</span>
      </FileDropZone>,
    );
    expect(screen.getByText('자식 요소')).toBeInTheDocument();
  });

  it('does not show overlay by default', () => {
    render(<FileDropZone onFileSelect={vi.fn()} />);
    expect(screen.queryByText('파일을 여기에 놓으세요')).not.toBeInTheDocument();
  });

  it('shows overlay on dragEnter', () => {
    const { container } = render(<FileDropZone onFileSelect={vi.fn()} />);
    fireEvent.dragEnter(getDropZone(container));
    expect(screen.getByText('파일을 여기에 놓으세요')).toBeInTheDocument();
  });

  it('applies active class on dragEnter', () => {
    const { container } = render(<FileDropZone onFileSelect={vi.fn()} />);
    const zone = getDropZone(container);
    fireEvent.dragEnter(zone);
    expect(zone).toHaveClass('file-drop-zone-active');
  });

  it('hides overlay after dragLeave (counter reaches 0)', () => {
    const { container } = render(<FileDropZone onFileSelect={vi.fn()} />);
    const zone = getDropZone(container);
    fireEvent.dragEnter(zone);
    fireEvent.dragLeave(zone);
    expect(screen.queryByText('파일을 여기에 놓으세요')).not.toBeInTheDocument();
  });

  it('tracks nested dragEnter/dragLeave correctly', () => {
    const { container } = render(<FileDropZone onFileSelect={vi.fn()} />);
    const zone = getDropZone(container);
    fireEvent.dragEnter(zone);
    fireEvent.dragEnter(zone); // nested enter
    fireEvent.dragLeave(zone); // counter: 2 -> 1
    expect(screen.getByText('파일을 여기에 놓으세요')).toBeInTheDocument();
    fireEvent.dragLeave(zone); // counter: 1 -> 0
    expect(screen.queryByText('파일을 여기에 놓으세요')).not.toBeInTheDocument();
  });

  it('calls onFileSelect with dropped file', () => {
    const onFileSelect = vi.fn();
    const { container } = render(<FileDropZone onFileSelect={onFileSelect} />);
    const zone = getDropZone(container);
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    fireEvent.drop(zone, { dataTransfer: { files: [file] } });
    expect(onFileSelect).toHaveBeenCalledWith(file);
  });

  it('does not call onFileSelect when disabled', () => {
    const onFileSelect = vi.fn();
    const { container } = render(<FileDropZone onFileSelect={onFileSelect} disabled />);
    const zone = getDropZone(container);
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    fireEvent.drop(zone, { dataTransfer: { files: [file] } });
    expect(onFileSelect).not.toHaveBeenCalled();
  });
});
