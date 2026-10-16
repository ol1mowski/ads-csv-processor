import { useId, useState, type DragEvent } from 'react';

const DROP_ZONE_CLASSES =
  'flex cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed px-4 py-8 text-center transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-indigo-500 peer-disabled:cursor-not-allowed peer-disabled:opacity-60';

interface FileDropZoneProps {
  readonly disabled: boolean;
  readonly onFileChosen: (file: File) => void;
}

export function FileDropZone({ disabled, onFileChosen }: FileDropZoneProps) {
  const [dragging, setDragging] = useState(false);
  const inputId = useId();
  const hintId = useId();

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    const dropped = event.dataTransfer.files[0];
    if (!disabled && dropped !== undefined) {
      onFileChosen(dropped);
    }
  }

  return (
    <div>
      <input
        id={inputId}
        type="file"
        accept=".csv,text/csv"
        aria-describedby={hintId}
        disabled={disabled}
        onChange={(event) => {
          const chosen = event.currentTarget.files?.[0];
          event.currentTarget.value = '';
          if (chosen !== undefined) {
            onFileChosen(chosen);
          }
        }}
        className="peer sr-only"
      />
      <label
        htmlFor={inputId}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(!disabled);
        }}
        onDragLeave={() => {
          setDragging(false);
        }}
        onDrop={handleDrop}
        className={`${DROP_ZONE_CLASSES} ${
          dragging
            ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-950'
            : 'border-slate-300 hover:border-indigo-400 dark:border-slate-700'
        }`}
      >
        <span className="font-medium">
          Przeciągnij plik tutaj lub{' '}
          <span className="text-indigo-600 underline dark:text-indigo-400">wybierz z dysku</span>
        </span>
      </label>
      <p id={hintId} className="mt-2 text-xs text-slate-500 dark:text-slate-400">
        Plik CSV do 5 GiB, separator „;”, nagłówek advertiserId;…;advertiserMargin.
      </p>
    </div>
  );
}
