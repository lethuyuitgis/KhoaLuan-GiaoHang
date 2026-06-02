import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '@/stores/auth-store';

/**
 * Subscribes to /topic/admin/orders via STOMP/SockJS. On every message,
 * invalidates the ['admin', 'orders'] TanStack Query so the OrdersPage
 * refetches.
 *
 * Auth: passes the admin JWT in the STOMP CONNECT frame's Authorization
 * header. The backend's WebSocketConfig (P9) maps this to AdminPrincipalWrapper.
 *
 * Connection lifecycle:
 *   - Effect mounts → connect → subscribe
 *   - Token changes → disconnect + reconnect (`token` is in dep array)
 *   - Component unmounts → deactivate (sends DISCONNECT frame)
 *
 * Errors are logged but never thrown — the 30s polling on OrdersPage
 * covers any WS gap, so a flaky broker doesn't break the UI.
 */
export function useAdminOrdersSocket(): void {
  const queryClient = useQueryClient();
  const token = useAuthStore((s) => s.auth?.accessToken);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!token) return;

    // Same-origin SockJS endpoint. Nginx proxies /ws/ to backend:8080.
    // The path '/ws' (no trailing slash on backend, but SockJS appends '/info', '/xhr_send', etc.)
    // matches the endpoint registered in WebSocketConfig#registerStompEndpoints.
    const sockJsFactory = () => new SockJS('/ws');

    const client = new Client({
      webSocketFactory: sockJsFactory,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5_000,        // 5s between reconnect attempts on disconnect
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => {},               // silence STOMP's verbose default logger

      onConnect: () => {
        console.debug('[admin-ws] connected');
        client.subscribe('/topic/admin/orders', (msg) => {
          try {
            const body = JSON.parse(msg.body);
            console.debug('[admin-ws] event', body?.type, body?.orderCode);
          } catch {
            // ignore parse errors — invalidate regardless
          }
          queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
        });
      },

      onStompError: (frame) => {
        // Server sent ERROR frame (e.g. bad JWT). Log + give up; polling covers it.
        console.warn('[admin-ws] stomp error', frame.headers.message);
      },

      onWebSocketError: (event) => {
        console.warn('[admin-ws] websocket error', event);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      void client.deactivate();
      clientRef.current = null;
    };
  }, [token, queryClient]);
}
