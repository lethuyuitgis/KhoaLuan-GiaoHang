export interface ApiError {
  code: string;
  message: string;
  traceId: string;
  timestamp: string;
  errors?: { field: string; message: string }[];
}
