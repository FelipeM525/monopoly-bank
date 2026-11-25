import { useMutation } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { ApiError, apiClient, type ExportFormat } from '@/api/client';
import { Button } from '@/components/ui/button';
import { saveBlob } from '@/utils/download';

const EXPORT_FORMATS: { format: ExportFormat; label: string }[] = [
  { format: 'csv', label: 'CSV' },
  { format: 'json', label: 'JSON' },
];

interface ExportHistoryButtonsProps {
  gameId: number;
}

export function ExportHistoryButtons({ gameId }: ExportHistoryButtonsProps) {
  const exportMutation = useMutation({
    mutationFn: (format: ExportFormat) =>
      apiClient.downloadTransactionsExport(gameId, format),
    onSuccess: ({ blob, filename }) => saveBlob(blob, filename),
  });

  const errorMessage = exportMutation.isError
    ? exportMutation.error instanceof ApiError
      ? exportMutation.error.detail
      : 'Export failed. Please try again.'
    : null;

  return (
    <div className="flex flex-col items-start gap-1 sm:items-end">
      <div
        role="group"
        aria-label="Export full history"
        className="flex items-center gap-2"
      >
        {EXPORT_FORMATS.map(({ format, label }) => (
          <Button
            key={format}
            type="button"
            variant="outline"
            size="sm"
            disabled={exportMutation.isPending}
            onClick={() => exportMutation.mutate(format)}
          >
            <Download className="h-4 w-4" />
            Export {label}
          </Button>
        ))}
      </div>
      {errorMessage && (
        <p role="alert" className="text-xs font-medium text-destructive">
          {errorMessage}
        </p>
      )}
    </div>
  );
}
