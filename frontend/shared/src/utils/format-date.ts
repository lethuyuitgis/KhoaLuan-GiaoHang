import { format, formatDistanceToNow, parseISO } from 'date-fns';
import { vi } from 'date-fns/locale';

export function formatDateTime(iso: string): string {
  try {
    return format(parseISO(iso), "HH:mm dd/MM/yyyy", { locale: vi });
  } catch {
    return iso;
  }
}

export function formatRelative(iso: string): string {
  try {
    return formatDistanceToNow(parseISO(iso), { locale: vi, addSuffix: true });
  } catch {
    return iso;
  }
}
