import { useCallback, useRef, useState, type ReactElement, type ReactNode, type DragEvent } from 'react';

type FileDropZoneProps = Readonly<{
  onFileSelect: (file: File) => void;
  disabled?: boolean;
  children?: ReactNode;
}>;

export function FileDropZone({ onFileSelect, disabled = false, children }: FileDropZoneProps): ReactElement {
  const [isDragging, setIsDragging] = useState(false);
  const dragCountRef = useRef(0);

  const handleDragEnter = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCountRef.current += 1;
    if (dragCountRef.current === 1) setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCountRef.current -= 1;
    if (dragCountRef.current === 0) setIsDragging(false);
  }, []);

  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCountRef.current = 0;
    setIsDragging(false);
    if (disabled) return;
    const file = e.dataTransfer.files[0] ?? null;
    if (file !== null) onFileSelect(file);
  }, [disabled, onFileSelect]);

  return (
    <div
      className={`file-drop-zone${isDragging ? ' file-drop-zone-active' : ''}`}
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {children}
      {isDragging ? (
        <div className="file-drop-overlay">
          <p>파일을 여기에 놓으세요</p>
        </div>
      ) : null}
    </div>
  );
}
